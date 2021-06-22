package dpf.sp.gpinf.indexer.desktop.themes;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

public abstract class Theme {
    private final Map<String, Color> dockMap = new HashMap<String, Color>();

    public abstract String getName();

    public abstract void apply();

    @Override
    public String toString() {
        return getName();
    }

    public void put(Object key, Object value) {
        UIManager.put(key, value);
        UIManager.getLookAndFeelDefaults().put(key, value);
    }

    public void putDock(String key, Color value) {
        dockMap.put(key, value);
    }
    
    public Map<String, Color> getDockSettings() {
        return Collections.unmodifiableMap(dockMap);
    }
}
