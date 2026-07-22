package edu.mit.media.mysnapshot.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import edu.mit.media.mysnapshot.R;

public class IntroActivity extends AppCompatActivity {

    public static final String LOGTAG = IntroActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_intro);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IntroActivity.this, SettingsActivity.class);
                startActivity(intent);
                finish();
            }
        });

    }

}