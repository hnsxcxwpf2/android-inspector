package com.github.cmzf.androidinspector;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.MessageFormat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUESR_SCREEN_CAPTURE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.activity_name);

        Global.setMainActivity(this);
        Global.setMainApplication(this.getApplication());

        Global.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                String ip = Utils.getWifiIpAddress();
                String message = MessageFormat.format((ip.equals("") ? "" : "http://{0}:{1}\n- or -\n") +
                        "adb forward tcp:{1} tcp:{1}\nhttp://localhost:{1}", ip, getServerPortView().getText());

                runOnUiThread(() -> {
                    getInspectorUrlView().setText(message);
                });

                Global.getMainHandler().postDelayed(this, 5000);
            }
        });
    }

    private TextView getInspectorUrlView() {
        return this.findViewById(R.id.inspectorUrl);
    }

    private TextView getServerPortView() {
        return this.findViewById(R.id.serverPort);
    }

    private Button getServerTogglerView() {
        return this.findViewById(R.id.serverToggler);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.inspectorUrl:
                copyInspectorUrl(view);
                break;
            case R.id.serverToggler:
                toggleInspectorServer(view);
                break;
            case R.id.checkVersion:
                forkMeOnGithub(view);
                break;
        }
    }

    private void forkMeOnGithub(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cmzf/android-inspector")));
    }

    private void delayToggleButton(Button button, String text) {
        button.setEnabled(false);
        button.setBackgroundColor(Color.parseColor(text.toLowerCase().equals("start") ? "#33CC33" : "#CC3333"));
        button.getBackground().setAlpha(100);

        Global.getMainHandler().post(new Runnable() {
            private float delay = 3.0f;
            private float step = 0.1f;

            @Override
            public void run() {
                MainActivity.this.runOnUiThread(() -> {
                    button.setText(String.format("%.1f", delay));
                    delay -= step;
                    if (delay > 0) {
                        Global.getMainHandler().postDelayed(this, (long) (step * 1000 / 2)); // half for ui update
                    } else {
                        Global.getMainHandler().post(() -> runOnUiThread(() -> {
                            button.setEnabled(true);
                            button.setText(text);
                            button.getBackground().setAlpha(255);
                        }));
                    }
                });
            }
        });
    }

    private void toggleInspectorServer(View view) {
        Button button = (Button) view;
        TextView portView = getServerPortView();
        TextView urlView = getInspectorUrlView();

        if (button.getText().toString().toLowerCase().equals("start")) {
            if (!Global.getScreenCaptureService().hasPermission()) {
                Global.getScreenCaptureService().requestProjection(REQUESR_SCREEN_CAPTURE);
                return;
            }

            button.setEnabled(false);
            button.setBackgroundColor(Color.LTGRAY);

            Utils.ensureAccessibilityServiceEnabled(() -> {
                bringMeFront();

                int port;
                try {
                    port = Integer.valueOf(String.valueOf(portView.getText()));
                    if (port < 1024 || port > 65535) {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "port not allowed!", Toast.LENGTH_LONG).show();

                    runOnUiThread(() -> {
                        button.setEnabled(true);
                        button.setBackgroundColor(Color.parseColor("#33CC33"));
                    });
                    return;
                }

                runOnUiThread(() -> {
                    portView.setEnabled(false);
                    urlView.setVisibility(View.VISIBLE);
                });


                Global.getInspectorServer().startServer(port);
                Global.getScreenCaptureService().startProjection();

                delayToggleButton(button, "STOP");
            }, () -> {
                bringMeFront();

                runOnUiThread(() -> {
                    toggleInspectorServer(view);
                });
            });
        } else {
            portView.setEnabled(true);
            urlView.setVisibility(View.INVISIBLE);

            Global.getInspectorServer().stopServer();
            Global.getScreenCaptureService().stopProjection();

            delayToggleButton(button, "START");
        }
    }

    private void bringMeFront() {
        final Intent intent = new Intent(this, getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    private void copyInspectorUrl(View view) {
        Utils.setClipBoard(getInspectorUrlView().getText());
        Toast.makeText(this, "copied!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Global.getScreenCaptureService().onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUESR_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            toggleInspectorServer(getServerTogglerView());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Global.getScreenCaptureService().stopProjection();
    }
}
