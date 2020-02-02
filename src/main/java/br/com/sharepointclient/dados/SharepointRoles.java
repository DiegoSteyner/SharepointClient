package br.com.sharepointclient.dados;

public enum SharepointRoles {
    
    CONTROLE_TOTAL("Controle Total"),
    DESIGNER("Design"),
    EDITAR("Editar"),
    COLABORACAO("Colaboração"),
    LEITURA("Leitura"),
    APROVAR("Aprovar"),
    GERENCIAR_HIERARQUIA("Gerenciar hierarquia"),
    LEITURA_RESTRITA("Leitura restrita"),;
    
    private String roleName;
    
    private SharepointRoles(String roleName)
    {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
