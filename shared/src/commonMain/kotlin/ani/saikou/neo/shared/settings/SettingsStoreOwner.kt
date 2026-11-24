package ani.saikou.neo.shared.settings

/**
 * Implemented by the app's Application, which holds the one [SettingsStore] a file can have. The phone and the TV
 * both reach the store through it, so neither UI module needs the other.
 */
public interface SettingsStoreOwner {
    public val settingsStore: SettingsStore
}
