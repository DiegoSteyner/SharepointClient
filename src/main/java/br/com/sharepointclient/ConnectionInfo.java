package br.com.sharepointclient;

import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;

public class ConnectionInfo {
    
    private HttpClientContext context;
    private CloseableHttpClient httpclient;
    private boolean connectionSuscess;
    private String connectionToken;
    
    private String connectionFailureMessage;
    private int connectionFailuteCode;
    
    public String getConnectionFailureMessage() {
        return connectionFailureMessage;
    }

    public void setConnectionFailureMessage(String connectionFailureMessage) {
        this.connectionFailureMessage = connectionFailureMessage;
    }

    public int getConnectionFailuteCode() {
        return connectionFailuteCode;
    }

    public void setConnectionFailuteCode(int connectionFailuteCode) {
        this.connectionFailuteCode = connectionFailuteCode;
    }

    public String getConnectionToken() {
        return connectionToken;
    }

    public void setConnectionToken(String connectionToken) {
        this.connectionToken = connectionToken;
    }

    public HttpClientContext getContext() {
        return context;
    }
    
    public void setContext(HttpClientContext context) {
        this.context = context;
    }
    
    public CloseableHttpClient getHttpclient() {
        return httpclient;
    }
    
    public void setHttpclient(CloseableHttpClient httpclient) {
        this.httpclient = httpclient;
    }
    
    public boolean isConnectionSuscess() {
        return connectionSuscess;
    }
    
    public void setConnectionSuscess(boolean connectionSuscess) {
        this.connectionSuscess = connectionSuscess;
    }
}
