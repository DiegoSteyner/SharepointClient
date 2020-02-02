package br.com.sharepointclient;

import java.io.File;

import br.com.sharepointclient.dados.ResponseStatus;
import br.com.sharepointclient.dados.SharepointRoles;

public class Principal {

    private static final String sharepointList 			= "<a lista no sharepoint que se deseja interagir, por exemplo, minhaListaPessoal>";
	private static final String sharepointUserLogin 	= "<usuario sharepoint com permissões>";
	private static final String sharepointUserPassword 	= "<senha do usuário>";
	
	private static final String cacertPassword 			= "<senha do cacert com o certificado https do sharepoint>";
	private static final String cacertPath 				= "<endereço do cacert com o certificado https do sharepoint>";
	
	private static final String sharepointHost 			= "<host do sharepoint, por exemplo minha.organizacao.com.br>";
	public static final String sharepointSiteString 	= "<site do sharepoint, por exemplo, /portal/meusite>";
	
    public static void main(String[] args) 
    {
        try 
        {
            SharepointFacede shpFacede = new SharepointFacede(sharepointHost, sharepointSiteString, cacertPath, cacertPassword);
            ConnectionInfo con = shpFacede.ntlmSharepointAuthenticate(sharepointUserLogin, sharepointUserPassword, true);
            
            String libraryFileLocation = sharepointSiteString+"/" + sharepointList + "/criadoByJava.txt";
            
            if(con.isConnectionSuscess())
            {
                ResponseStatus fileStatus = shpFacede.fileExistInLibrary(con, libraryFileLocation);
                
                if(fileStatus.equals(ResponseStatus.ARQUIVO_NAO_EXISTE))
                {
                    fileStatus = shpFacede.sendFileToSharepoint(con, libraryFileLocation, new File("D:\\a1\\filetemp.txt"));
                }
                
                System.out.println(fileStatus);
                
                if(fileStatus.equals(ResponseStatus.ARQUIVO_EXISTE) || fileStatus.equals(ResponseStatus.ARQUIVO_CRIADO))
                {
                    if(fileStatus.equals(ResponseStatus.ARQUIVO_CRIADO))
                    {
                        if(shpFacede.breakRoleInheritanceOfFile(con, libraryFileLocation).equals(ResponseStatus.HIERARQUIA_SEC_QUEBRADA))
                        {
                            String roleId = shpFacede.getRoleDefinitionID(con, SharepointRoles.EDITAR);
                            shpFacede.addRoleOnFile(con, libraryFileLocation, "44", roleId);
                            
                            System.out.println(shpFacede.moveFileTo(con, libraryFileLocation, sharepointSiteString+"/" + sharepointList + "/outra pasta/criadoByJava.txt"));
                        }
                    }
                    else
                    {
                        System.out.println(shpFacede.deleteFile(con, libraryFileLocation));
                    }
                }
            }
            else 
            {
                System.out.println("Não é possível conectar");
                System.out.println("O servidor retornou a mensagem "+con.getConnectionFailureMessage()+" com o seguinte código de erro "+con.getConnectionFailuteCode());
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}
