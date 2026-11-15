package example.custom; public final class ThrowingPet extends PetEntry { public ThrowingPet() { throw new AssertionError("Static inspection must never run this"); } }
