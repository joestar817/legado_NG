-keep class io.legado.app.quickjs.QuickJsSandboxBridge {
    public <init>(android.content.Context);
    public java.lang.String evalString(java.lang.String);
    public java.lang.String evalStringWithData(java.lang.String, java.lang.String);
}

-keep class io.legado.app.quickjs.QuickJsSandboxService {
    public <init>();
}

-keep class io.legado.app.quickjs.V8SandboxBridge {
    public <init>(android.content.Context);
    public java.lang.String evalString(java.lang.String);
    public java.lang.String evalStringWithData(java.lang.String, java.lang.String);
}
