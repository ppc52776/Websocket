package com.innodev.websocketssl;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.framing.Framedata;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by ppc on 2015/9/25.
 */
public class WebSocketSSL {
    private static final String TAG = "WebSocketSSL";
    private static final int pingpong_duration = 30000; // mini-seconds
    private Context context;
    private WebSocketClient webSocketClient;
    private String url;
    private String sslFile;
    //private OnEventHandler onEventHandler;
    private PingPongThread pingPongThread;
    private Map<String, EventHandler> eventHandler;

    public WebSocketSSL(Context context, String url, String sslFile) {

        WebSocketImpl.DEBUG = true;

        this.context = context;
        this.url = url;
        this.sslFile = sslFile;
        //this.onEventHandler = onEventHandler;
        this.eventHandler = new HashMap<>();

        CreateWebSocketClient();
    }

    public void emit(String event, String msg) {
        if(webSocketClient.isOpen()) {
            try {
                JSONObject json = new JSONObject()
                        .put("event", event)
                        .put("data", msg);
                webSocketClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void on(String event, EventHandler handler) {
        eventHandler.put(event, handler);
    }

    public interface EventHandler {
        void handle(String msg);
    }

    private class PingPongThread extends Thread {

        @Override
        public void run() {
            FramedataImpl1 framedata = new FramedataImpl1(Framedata.Opcode.PING);
            framedata.setFin(true);

            boolean run = true;
            while(run) {
                try {
                    Thread.sleep(pingpong_duration);
                    Log.i(TAG, "Sending PING");
                    if(!webSocketClient.isOpen()) {
                        Log.i(TAG, "webSocketClient is not connected, skip ping.");
                        run = false;
                    } else {
                        webSocketClient.sendFrame(framedata);
                    }
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                    run = false;
                    Log.i(TAG, "Stop ping pong thread");
                }
            }
            Log.i(TAG, "ping pong thread stopped");
        }
    }

    public static class Builder {
        private Context context;
        private String sslCertFile;
        private String url;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setSSLCert(String asset_name) {
            this.sslCertFile = asset_name;
            return this;
        }

        public Builder setURL(String url) {
            this.url = url;
            return this;
        }
    }

    private void CreateWebSocketClient() {

        Map<String,String> headers = new HashMap<>();

        URI uri = null;
        try {
            uri = new URI(this.url);
            headers.put("Origin", "http://" + uri.getHost());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        webSocketClient = new WebSocketClient(uri, new Draft_17(), headers, 5000) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.i(TAG, "onOpen");
                pingPongThread = new PingPongThread();
                pingPongThread.start();
            }

            @Override
            public void onMessage(String message) {
                Log.i(TAG, "onMessage: " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    String event = json.getString("event");
                    if(eventHandler.containsKey(event)) {
                        eventHandler.get(event).handle(json.getString("data"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.i(TAG, String.format("onClose: code=%d, reason=%s, remote=%s",
                        code, reason, Boolean.valueOf(remote)));
                pingPongThread.interrupt(); // stop ping-pong

                Log.i(TAG, "Try reconnecting in 5 second, state=" + webSocketClient.getReadyState());
                reConnect();
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "onError: " + ex);
                ex.printStackTrace();
            }
        };

        webSocketClient.setSocket(createSSLSocket());
        webSocketClient.connect();
    }

    private void reConnect() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                boolean running = true;
                try {
                    while(running) {
                        Thread.sleep(5000);
                        if(isNetworkAvailable()) {
                            running = false;
                            CreateWebSocketClient();
                        } else {
                            Log.i(TAG, "Network is not available, retry later");
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Socket createSSLSocket() {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            // Load CAs from an InputStream
            Certificate ca = cf.generateCertificate(context.getAssets().open(sslFile));

            // Create a KeyStore containing our trusted CAs
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext.getSocketFactory().createSocket();
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager conMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnected();
    }
}
