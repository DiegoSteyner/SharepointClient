package br.com.sharepointclient;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import br.com.sharepointclient.dados.ResponseStatus;
import br.com.sharepointclient.dados.SharepointRoles;

public class SharepointFacede {
    
    private static final String HTTP_PROTOCOL = "https";
    private static final int HTTP_PORT = 443;
    
    private HttpHost sharepointHost = null;
    private String sharepointSite = null;
    
    /**
     * Construtor parametrizado
     * 
     * @param sharepointHost O endere�o do host do Sharepoint
     * @param sharepointSite O site hospedado no host
     */
    public SharepointFacede(String sharepointHost, String sharepointSite, String cacertsPath, String cacertsPassword) 
    {
        System.setProperty("javax.net.ssl.trustStore", cacertsPath);
        System.setProperty("javax.net.ssl.trustStorePassword", cacertsPassword);
        
        this.sharepointHost = new HttpHost(sharepointHost, HTTP_PORT, HTTP_PROTOCOL);
        this.sharepointSite = sharepointSite;
    }
    
    /**
     * M�todo que cria uma conex�o est�vel com o servidor Sharepoint
     * 
     * @param userName O nome do usu�rio de rede a ser autenticado
     * @param password O password do usu�rio de rede a ser autenticado
     * @param fromRoot Se True, a conex�o ser� � partir do root desde que a porta esteja habilitada
     * 
     * @return O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ConnectionInfo ntlmSharepointAuthenticate(String userName, String password, boolean fromRoot) throws ClientProtocolException, IOException
    {
        ConnectionInfo connectionInfo = new ConnectionInfo();
        CloseableHttpClient httpclient = HttpClients.custom().setRetryHandler(new DefaultHttpRequestRetryHandler(0, false)).build();

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new NTCredentials(userName, password, "", ""));

        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        HttpHead request1 = new HttpHead((fromRoot ? "/" : ""));
        CloseableHttpResponse response = null;
        
        try 
        {
            response = httpclient.execute(sharepointHost, request1, context);
            EntityUtils.consume(response.getEntity());
            
            connectionInfo.setContext(context);
            connectionInfo.setHttpclient(httpclient);
            connectionInfo.setConnectionSuscess(response.getStatusLine().getStatusCode() == 200);
            
            if(connectionInfo.isConnectionSuscess())
            {
                addAuthorizationToken(connectionInfo);
            }
            else
            {
                connectionInfo.setConnectionFailureMessage(response.getStatusLine().getReasonPhrase());
                connectionInfo.setConnectionFailuteCode((response.getStatusLine().getStatusCode()));
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
        
        return(connectionInfo);
    }
    
    /**
     * M�todo que adiciona um um BearToken a conex�o
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @return O Objeto {@link ConnectionInfo} com o BearToken configurado
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ConnectionInfo addAuthorizationToken(ConnectionInfo con) throws ClientProtocolException, IOException
    {
        HttpPost targetRequest = new HttpPost(sharepointSite+"/_api/contextinfo");
        CloseableHttpResponse response = null;

        try 
        {
            targetRequest.addHeader("Accept", "application/json;odata=verbose");
            targetRequest.addHeader("content-type", "application/json;odata=verbose");
            
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            
            JSONObject jsonObject = new JSONObject(EntityUtils.toString(response.getEntity()));
            EntityUtils.consume(response.getEntity());
            
            String jsonStr = jsonObject.toMap().get("d").toString();
            jsonStr = jsonStr.substring(jsonStr.indexOf("FormDigestValue=")+16, jsonStr.lastIndexOf("__metadata")-2);
            
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc == HttpStatus.SC_OK) 
            {
                con.setConnectionToken(jsonStr);
            } 
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
        
        return(con);
    }

    /**
     * M�todo que envia um arquivo da m�quina para o site Sharepoint
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo no formato /site/subsite/path de onde o arquivo deve ser salvo
     * @param localFileLocation O endere�o do arquivo na m�quina local
     * @return O {@link ResponseStatus} da requisi��o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     * @throws Exception Exce��o lan�ada caso algum erro desconhecido seja lan�ado pelo servidor
     */
    public ResponseStatus sendFileToSharepoint(ConnectionInfo con, String libraryFileLocation, File localFileLocation) throws IOException, ClientProtocolException, Exception 
    {
        HttpPut targetResquest = new HttpPut(encodePath(libraryFileLocation)); 
        targetResquest.setEntity(new FileEntity(localFileLocation));
        CloseableHttpResponse response = null;
        
        try 
        {
            response = con.getHttpclient().execute(sharepointHost, targetResquest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();
            
            if (rc == HttpStatus.SC_CREATED) 
            {
                return(ResponseStatus.ARQUIVO_CRIADO);
            } 
            else if (rc == HttpStatus.SC_OK) 
            {
                return(ResponseStatus.ARQUIVO_SOBRESCRITO);
            } 
            else 
            {
                throw new Exception("Houve um problema ao criar o arquivo na localiza��o [ "+localFileLocation+" ] pela raz�o [ "+reason+" ] httpcode [ " +rc+" ]");
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que verifica se um arquivo existe
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @return O {@link ResponseStatus} da requisi��o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ResponseStatus fileExistInLibrary(ConnectionInfo con, String libraryFileLocation) throws ClientProtocolException, IOException 
    {
        HttpGet targetRequest = new HttpGet(sharepointSite+"/_api/web/GetFileByServerRelativeUrl('" + encodePath(libraryFileLocation) + "')");
        CloseableHttpResponse response = null;

        try 
        {
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc != HttpStatus.SC_OK) 
            {
                return (ResponseStatus.ARQUIVO_NAO_EXISTE);
            } 
            else 
            {
                return (ResponseStatus.ARQUIVO_EXISTE);
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que move um arquvo dentro da mesma library no Sharepoint
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @param newLocationOnSameLibrary O endere�o relativo de onde o arquivo ser� armazenado no formato /site/subsite/path 
     * @return O {@link ResponseStatus} da requisi��o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ResponseStatus moveFileTo(ConnectionInfo con, String libraryFileLocation, String newLocationOnSameLibrary) throws ClientProtocolException, IOException
    {
        HttpPost targetRequest = new HttpPost(sharepointSite+"/_api/web/GetFileByServerRelativeUrl('" + encodePath(libraryFileLocation) + "')/moveto(newurl='"+encodePath(newLocationOnSameLibrary)+"',flags=1)");
        CloseableHttpResponse response = null;

        try 
        {
            targetRequest.addHeader("Accept", "application/json;odata=verbose");
            targetRequest.addHeader("content-type", "application/json;odata=verbose");
            targetRequest.addHeader("X-RequestDigest", con.getConnectionToken());
            
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc == HttpStatus.SC_OK) 
            {
                return (ResponseStatus.ARQUIVO_MOVIDO);
            } 
            else 
            {
                return (ResponseStatus.ARQUIVO_NAO_MOVIDO);
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que retorna o ID de uma Role no Sharepoint
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param role O {@link SharepointRoles} que se deseja saber o ID
     * 
     * @return O ID da Role no Sharepoint
     * @throws Exception Caso ocorra algum erro, uma exce��o ser� lan�ada
     */
    public String getRoleDefinitionID(ConnectionInfo con, SharepointRoles role) throws Exception 
    {
        HttpGet targetRequest = new HttpGet(sharepointSite+"/_api/web/roledefinitions/getByName('" + role.getRoleName() + "')");
        CloseableHttpResponse response = null;

        try 
        {
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            Document document = parseStringToDocument(EntityUtils.toString(response.getEntity()));
            
            EntityUtils.consume(response.getEntity());
            
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc == HttpStatus.SC_OK) 
            {
                return (document.getFirstChild().getLastChild().getChildNodes().item(0).getChildNodes().item(3).getTextContent());
            } 
            else 
            {
                return (null);
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que remove as permiss�es de um grupo no arquivo
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @param groupID O ID do grupo que deve ser removido
     * 
     * @return Se True, a permiss�o do grupo foi removida
     * @throws Exception Caso ocorra algum erro, uma exce��o ser� lan�ada
     */
    public boolean removeGroupOnPermissionSet(ConnectionInfo con, String libraryFileLocation, String groupID) throws Exception 
    {
        HttpDelete targetRequest = new HttpDelete(sharepointSite+"/_api/web/GetFileByServerRelativeUrl('" + encodePath(libraryFileLocation) + "')/ListItemAllFields/roleassignments/getbyprincipalid("+groupID+")");
        CloseableHttpResponse response = null;

        try 
        {
            targetRequest.addHeader("Accept", "application/json;odata=verbose");
            targetRequest.addHeader("content-type", "application/json;odata=verbose");
            targetRequest.addHeader("X-RequestDigest", con.getConnectionToken());
            
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
        
            return(rc == HttpStatus.SC_OK);
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que deleta um arquivo na Library
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @return Se True, O arquivo foi deletado com sucesso
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public boolean deleteFile(ConnectionInfo con, String libraryFileLocation) throws ClientProtocolException, IOException
    {
        HttpDelete request2 = new HttpDelete(encodePath(libraryFileLocation));
        CloseableHttpResponse resp = con.getHttpclient().execute(sharepointHost,request2, con.getContext());
        
        return(resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK || resp.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT);
    }
    
    /**
     * M�todo que desvincula a seguran�a do arquivo da seguran�a da lista
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @return O {@link ResponseStatus} da requisi��o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ResponseStatus breakRoleInheritanceOfFile(ConnectionInfo con, String libraryFileLocation) throws ClientProtocolException, IOException 
    {
        HttpPost targetRequest = new HttpPost(sharepointSite+"/_api/web/GetFileByServerRelativeUrl('" + encodePath(libraryFileLocation) + "')/ListItemAllFields/breakroleinheritance(true)");
        CloseableHttpResponse response = null;

        try 
        {
            targetRequest.addHeader("Accept", "application/json;odata=verbose");
            targetRequest.addHeader("content-type", "application/json;odata=verbose");
            targetRequest.addHeader("X-RequestDigest", con.getConnectionToken());
            
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc == HttpStatus.SC_OK) 
            {
                return (ResponseStatus.HIERARQUIA_SEC_QUEBRADA);
            } 
            else 
            {
                return (ResponseStatus.HIERARQUIA_SEC_MANTIDA);
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que adiciona uma nova Role para um grupo em um arquivo
     * 
     * @param con O Objeto {@link ConnectionInfo} com as informa��e de conex�o
     * @param libraryFileLocation O endere�o relativo do arquivo no formato /site/subsite/path
     * @param groupId O ID do grupo no permissionSet
     * @param roleDefinitionId O ID da role a ser adicionada ao grupo
     * @return O {@link ResponseStatus} da requisi��o
     * 
     * @throws ClientProtocolException Exce��o lan�ada caso haja alguma falha na rede ou na comunica��o HTTP 
     * @throws IOException Exce��o lan�ada caso haja alguma falha na leitura ou escrita de dados em mem�ria
     */
    public ResponseStatus addRoleOnFile(ConnectionInfo con, String libraryFileLocation, String groupId, String roleDefinitionId) throws ClientProtocolException, IOException 
    {
        HttpPost targetRequest = new HttpPost(sharepointSite+"/_api/web/GetFileByServerRelativeUrl('" + encodePath(libraryFileLocation) + "')/ListItemAllFields/roleassignments/addroleassignment(principalid="+groupId+",roledefid="+roleDefinitionId+")");
        CloseableHttpResponse response = null;

        try 
        {
            targetRequest.addHeader("Accept", "application/json;odata=verbose");
            targetRequest.addHeader("content-type", "application/json;odata=verbose");
            targetRequest.addHeader("X-RequestDigest", con.getConnectionToken());
            
            response = con.getHttpclient().execute(sharepointHost, targetRequest, con.getContext());
            EntityUtils.consume(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
        
            if (rc == HttpStatus.SC_OK) 
            {
                return (ResponseStatus.HIERARQUIA_SEC_QUEBRADA);
            } 
            else 
            {
                return (ResponseStatus.HIERARQUIA_SEC_MANTIDA);
            }
        } 
        finally 
        {
            if (response != null)
            {
                response.close();
            }
        }
    }
    
    /**
     * M�todo que transforma uma String em um {@link Document}
     * 
     * @param xml A String do XML
     * 
     * @return O {@link Document} gerado
     * @throws Exception Caso ocorra algum erro, uma exce��o ser� lan�ada
     */
    private static Document parseStringToDocument(String xml) throws Exception
    {
       DocumentBuilderFactory fctr = DocumentBuilderFactory.newInstance();
       DocumentBuilder bldr = fctr.newDocumentBuilder();
       InputSource insrc = new InputSource(new StringReader(xml));
       return bldr.parse(insrc);
    }
    
    /**
     * M�todo que faz o encoding das URL's passadas
     * 
     * @param path A Url passada
     * 
     * @return Uma String encoded da URL, Caso a String j� esteja Encoded, a pr�pria String � retornada
     * @throws UnsupportedEncodingException Caso o Enconding n�o seja suportado, uma exce��o ser� lan�ada.
     */
    private String encodePath(String path) throws UnsupportedEncodingException
    {
        if(path != null && !path.contains("%"))
        {
            return(UriUtils.encodeQuery(path, "UTF-8"));
        }
        
        return(path);
    }
    
    public HttpHost getSharepointHost() {
        return sharepointHost;
    }

    public void setSharepointHost(HttpHost sharepointHost) {
        this.sharepointHost = sharepointHost;
    }

    public String getSharepointSite() {
        return sharepointSite;
    }

    public void setSharepointSite(String sharepointSite) {
        this.sharepointSite = sharepointSite;
    }
}
