package badgePrefix;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.text.Text;


import java.util.List;

@ConfigSerializable
public class Config {
    public static TypeToken<Config> type = TypeToken.of(Config.class);
    @Setting
    public List<Prefix> prefixes;
    @ConfigSerializable
    public static class Prefix {
        @Setting
        public String name;
        @Setting
        public String display;
    }
}
