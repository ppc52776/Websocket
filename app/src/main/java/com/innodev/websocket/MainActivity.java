package com.innodev.websocket;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.java_websocket.WebSocketImpl;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String URL = "wss://innodev-tech.com:8888/test/mychannel";
    WebSocketSSL webSocketSSL;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.textView);
        Button btn = (Button) findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                websocket();
            }
        });

        Button btn_send = (Button) findViewById(R.id.btn_send);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webSocketSSL.emit("new message", "hello~~~");
            }
        });
    }

    private void websocket() {
        WebSocketImpl.DEBUG = true;

        webSocketSSL = new WebSocketSSL(this,
                "wss://innodev-tech.com:8888/test/mychannel?key=12345",
                "startssl.crt");
        webSocketSSL.on("new message", new WebSocketSSL.EventHandler() {
            @Override
            public void handle(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(msg);
                    }
                });
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
