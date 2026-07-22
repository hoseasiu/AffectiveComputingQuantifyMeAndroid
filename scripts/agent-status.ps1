<#
.SYNOPSIS
  Derives the live state of every agent worktree, branch, PR and issue claim.

.DESCRIPTION
  This script replaces the old AGENT_PLANS/COORDINATION.md "active worktree" table.
  That table was a hand-maintained cache of state that git and GitHub already know,
  so it went stale the moment any agent crashed, was interrupted, or simply forgot to
  update it -- and it was stored in a gitignored file that got deleted outright.

  Nothing here is cached. Every value is read from ground truth at run time:
  `git worktree list`, `git status`, `git rev-list`, and `gh pr/issue list`.

  Run this at the START of any agent session, before picking up work.

.NOTES
  ALWAYS fetches first. Reading remote-tracking refs without fetching reports
  branches as "unpushed" and PRs as "missing" when they exist on the remote --
  a real misdiagnosis that this script exists partly to prevent.
#>

[CmdletBinding()]
param(
    # Skip the initial `git fetch` (offline use). Remote-derived columns may be stale.
    [switch]$NoFetch,
    # Suppress the GitHub sections (no `gh` available / not authenticated).
    [switch]$NoGitHub
)

$ErrorActionPreference = 'Stop'

# --- locate the main worktree -------------------------------------------------
# `git worktree list --porcelain` always lists the main worktree first.
$repoRoot = (git rev-parse --show-toplevel 2>$null)
if (-not $repoRoot) { Write-Error "Not inside a git repository."; return }
$mainWorktree = (git worktree list --porcelain | Select-Object -First 1) -replace '^worktree ', ''

function Write-Section($text) {
    Write-Host ""
    Write-Host "=== $text ===" -ForegroundColor Cyan
}

$problems = [System.Collections.Generic.List[string]]::new()
function Add-Problem($text) { $problems.Add($text) | Out-Null }

# --- fetch --------------------------------------------------------------------
if (-not $NoFetch) {
    Write-Host "Fetching origin (with prune)..." -ForegroundColor DarkGray
    git -C $mainWorktree fetch origin --prune --quiet 2>&1 | Out-Null
}

# --- GitHub state (fetched once, reused) --------------------------------------
$prs = @()
$issues = @()
if (-not $NoGitHub) {
    try {
        $prs = gh pr list --state all --limit 100 `
            --json number,state,title,headRefName,isDraft,url 2>$null | ConvertFrom-Json
        $issues = gh issue list --state open --limit 100 `
            --json number,title,labels,assignees,url 2>$null | ConvertFrom-Json
    } catch {
        Write-Warning "gh unavailable or not authenticated; skipping GitHub sections."
        $NoGitHub = $true
    }
}

function Get-PrFor($branch) {
    if (-not $branch) { return $null }
    # Prefer an open PR; fall back to the most recent closed/merged one.
    $match = @($prs | Where-Object { $_.headRefName -eq $branch })
    if (-not $match) { return $null }
    $open = @($match | Where-Object { $_.state -eq 'OPEN' })
    if ($open) { return $open[0] }
    return ($match | Sort-Object number -Descending)[0]
}

# --- walk worktrees -----------------------------------------------------------
Write-Section "Worktrees"

$wtBlocks = (git -C $mainWorktree worktree list --porcelain) -join "`n" -split "`n`n"
$rows = @()

foreach ($block in $wtBlocks) {
    if (-not $block.Trim()) { continue }
    $path = ([regex]::Match($block, '(?m)^worktree (.+)$')).Groups[1].Value.Trim()
    if (-not $path) { continue }

    $isDetached = $block -match '(?m)^detached\s*$'
    $branch = ''
    $bm = [regex]::Match($block, '(?m)^branch refs/heads/(.+)$')
    if ($bm.Success) { $branch = $bm.Groups[1].Value.Trim() }

    $isMain = ($path.TrimEnd('\','/') -eq $mainWorktree.TrimEnd('\','/'))

    # dirty?
    $dirtyLines = @(git -C $path status --porcelain 2>$null)
    $dirty = $dirtyLines.Count

    # ahead/behind vs origin/master (what actually matters for "has this produced work")
    $ahead = 0; $behind = 0; $pushState = 'n/a'
    $head = (git -C $path rev-parse HEAD 2>$null)

    if (git -C $path rev-parse --verify --quiet origin/master 2>$null) {
        $counts = (git -C $path rev-list --left-right --count "origin/master...HEAD" 2>$null)
        if ($counts) {
            $parts = $counts -split '\s+'
            $behind = [int]$parts[0]; $ahead = [int]$parts[1]
        }
    }

    # is the branch pushed, and is the local tip published?
    if ($branch) {
        $upstreamRef = "origin/$branch"
        if (git -C $path rev-parse --verify --quiet $upstreamRef 2>$null) {
            $remoteTip = (git -C $path rev-parse $upstreamRef 2>$null)
            $localTip  = (git -C $path rev-parse $branch 2>$null)
            $pushState = if ($remoteTip -eq $localTip) { 'pushed' } else { 'DIVERGED' }
        } else {
            $pushState = 'NOT PUSHED'
        }
    } elseif ($isDetached) {
        $pushState = 'detached'
    }

    $pr = Get-PrFor $branch
    $prText = if ($pr) { "#$($pr.number) $($pr.state)$(if($pr.isDraft){' (draft)'})" } else { '-' }

    $rows += [pscustomobject]@{
        Worktree = (Split-Path $path -Leaf)
        Branch   = if ($branch) { $branch } elseif ($isDetached) { '(detached)' } else { '?' }
        Dirty    = $dirty
        Ahead    = $ahead
        Behind   = $behind
        Push     = $pushState
        PR       = $prText
        Path     = $path
    }

    # ---- pathology detection -------------------------------------------------
    $label = Split-Path $path -Leaf

    if ($isMain) {
        if ($pushState -eq 'DIVERGED') { Add-Problem "main worktree: '$branch' differs from origin/$branch -- push or pull." }
        if ($dirty -gt 0)              { Add-Problem "main worktree has $dirty uncommitted file(s)." }
        continue
    }

    if ($dirty -gt 0) {
        Add-Problem "$label : $dirty uncommitted file(s) -- work exists only on this disk. Commit and push."
    }
    if ($pushState -eq 'NOT PUSHED' -and $ahead -gt 0) {
        Add-Problem "$label : $ahead commit(s) on '$branch' never pushed -- invisible to everyone else."
    }
    if ($pushState -eq 'DIVERGED') {
        Add-Problem "$label : local '$branch' differs from origin/$branch."
    }
    if ($ahead -gt 0 -and -not $pr) {
        Add-Problem "$label : $ahead commit(s) but NO pull request -- open one (a PR is the durable claim)."
    }
    if ($ahead -eq 0 -and $dirty -eq 0 -and -not $isDetached) {
        Add-Problem "$label : zero commits and clean -- looks claimed but has produced nothing. Reap it or use it."
    }
    if ($pr -and $pr.state -eq 'MERGED') {
        Add-Problem "$label : PR #$($pr.number) already MERGED -- worktree is finished, remove it (git worktree remove)."
    }
    if ($isDetached -and $ahead -eq 0 -and $dirty -eq 0) {
        Add-Problem "$label : detached HEAD, already in origin/master -- leftover, remove it."
    }

    # junction hazard: AGENT_PLANS must be a real checked-out dir, never a junction
    $apPath = Join-Path $path 'AGENT_PLANS'
    if (Test-Path $apPath) {
        $item = Get-Item $apPath -Force
        if ($item.LinkType -eq 'Junction') {
            Add-Problem "$label : AGENT_PLANS is a JUNCTION. Delete it with 'cmd /c rmdir' (NOT Remove-Item -Recurse, which deletes the target's contents)."
        }
    }
}

$rows | Format-Table Worktree, Branch, Dirty, Ahead, Behind, Push, PR -AutoSize

# --- issue claims -------------------------------------------------------------
if (-not $NoGitHub) {
    Write-Section "Claimed issues (label: agent:in-progress)"
    $claimed = @($issues | Where-Object { $_.labels.name -contains 'agent:in-progress' })
    if ($claimed) {
        $claimed | ForEach-Object {
            $who = if ($_.assignees) { ($_.assignees.login) -join ',' } else { 'unassigned' }
            Write-Host ("  #{0,-4} [{1}] {2}" -f $_.number, $who, $_.title)
        }
    } else {
        Write-Host "  (none)" -ForegroundColor DarkGray
    }

    Write-Section "Available issues (open, unclaimed)"
    $free = @($issues | Where-Object { $_.labels.name -notcontains 'agent:in-progress' })
    if ($free) {
        $free | Sort-Object number | ForEach-Object {
            Write-Host ("  #{0,-4} {1}" -f $_.number, $_.title)
        }
        Write-Host ""
        Write-Host "  Check AGENT_PLANS/DEPENDENCIES.md for blockers and file-overlap risk before claiming." -ForegroundColor DarkGray
    } else {
        Write-Host "  (none)" -ForegroundColor DarkGray
    }

    Write-Section "Open PRs"
    $openPrs = @($prs | Where-Object { $_.state -eq 'OPEN' })
    if ($openPrs) {
        $openPrs | Sort-Object number | ForEach-Object {
            Write-Host ("  #{0,-4} [{1}] {2}" -f $_.number, $_.headRefName, $_.title)
        }
    } else {
        Write-Host "  (none)" -ForegroundColor DarkGray
    }
}

# --- verdict ------------------------------------------------------------------
Write-Section "Problems"
if ($problems.Count -eq 0) {
    Write-Host "  None. All worktrees are pushed, PR'd, and accounted for." -ForegroundColor Green
} else {
    $problems | ForEach-Object { Write-Host "  ! $_" -ForegroundColor Yellow }
}
Write-Host ""

# This is a report, not a gate: always succeed, so a trailing native-command
# exit code (e.g. a probing `git rev-parse --verify`) never looks like a failure.
exit 0
