package example.custom;
public class PetEntry implements io.legado.pet.api.PetPlugin {
    public PetEntry() {}
    public String getId() { return "test_pet"; }
    public io.legado.pet.api.PetSession createSession(io.legado.pet.api.PetAssets assets) { return null; }
}
