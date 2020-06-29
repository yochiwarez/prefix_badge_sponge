package badgePrefix;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.scoreboard.Team;
import org.spongepowered.api.text.serializer.TextSerializers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.LuckPermsEvent;
import net.luckperms.api.event.log.LogPublishEvent;
import net.luckperms.api.event.player.PlayerDataSaveEvent;
import net.luckperms.api.event.sync.ConfigReloadEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import org.spongepowered.api.text.Text;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.service.ProviderRegistration;
import net.luckperms.api.event.sync.PostSyncEvent;
import net.luckperms.api.event.user.UserCacheLoadEvent;
import net.luckperms.api.event.user.UserLoadEvent;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import static org.spongepowered.api.command.args.GenericArguments.user;

@Plugin(id = "badge",
        name = "Badge",
        version = "1.0.0",
        description = "Puts prefixes on peoples' heads.",
        authors = {
            "pie_flavor",
            "yochiwarez"
        },
        dependencies = {
            @Dependency(id = "luckperms", optional = false)}
)

public class Badge {

    @Inject
    Game game;
    @Inject
    Logger logger;
    @Inject
    @DefaultConfig(sharedRoot = true)
    ConfigurationLoader<CommentedConfigurationNode> loader;
    @Inject
    @DefaultConfig(sharedRoot = true)
    Path path;
    Config config;
    Scoreboard scoreboard;
    LuckPerms lp;
    GroupManager gm;
    UserManager um;
    Scoreboard sb;
    ContextManager cm;
    EventBus eb;

    @Listener
    public void preInit(GamePreInitializationEvent e) throws IOException, ObjectMappingException {
        if (!Files.exists(path)) {
            try {
                game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(path);
            } catch (IOException ex) {
                logger.error("Could not copy default config!");
                mapDefault();
                throw ex;
            }
        }
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (IOException ex) {
            logger.error("Could not load config!");
            mapDefault();
            throw ex;
        }
        try {
            config = root.getValue(Config.type);
        } catch (ObjectMappingException ex) {
            logger.error("Invalid config!");
            loadDefault();
            throw ex;
        }

        Optional<ProviderRegistration<LuckPerms>> lpProvider = Sponge.getServiceManager().getRegistration(LuckPerms.class);

        lp = lpProvider.get().getProvider();

        gm = lp.getGroupManager();
        um = lp.getUserManager();
        cm = lp.getContextManager();
        eb = lp.getEventBus();

    }

    private void updateScoreboard() {

        List<Group> groups = new ArrayList<Group>(gm.getLoadedGroups());

        //Collections.sort(groups, new Comparator<Group>() {
        //@Override
        //public int compare(Group o1, Group o2) {
        //  return (o1.getWeight().getAsInt() > o2.getWeight().getAsInt()) ? 1 : (o1.getWeight().getAsInt() < o2.getWeight().getAsInt()) ? -1 : 0;
        //}
        //});
        List<Team> teams = new ArrayList<Team>();

        for (Group g : groups) {

            //logger.info(g.getWeight().toString());
            QueryOptions queryOptions = lp.getContextManager().getStaticQueryOptions();
            String lprefix = g.getCachedData().getMetaData(queryOptions).getPrefix();

            teams.add(
                    Team.builder()
                            .allowFriendlyFire(true)
                            .canSeeFriendlyInvisibles(false)
                            .name(g.getName())
                            .prefix(Text.of((lprefix + " &7").replace("&", "§")))
                            .build()
            );
        }

        sb = Scoreboard.builder().teams(teams).build();

        for (Player p : game.getServer().getOnlinePlayers()) {

            User u = um.getUser(p.getUniqueId());

            List<String> userGroups = new ArrayList<String>(u.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toSet()));
            
            Collections.sort(userGroups, new Comparator<String>(){
                @Override
                public int compare(String o1, String o2) {
                    
                    Integer gw1 = gm.getGroup(o1).getWeight().orElse(0);
                    Integer gw2 = gm.getGroup(o2).getWeight().orElse(0);
                    
                    return (gw1 > gw2) ? -1 : (gw1 < gw2) ? 1 : 0;
                }
            });

       //     int maxWeight = u.getNodes().stream()
     //               .filter(NodeType.::matches)
   //                 .map(NodeType.PREFIX::cast)
//                    .filter(
//                        n -> n.getContexts().getAnyValue(DefaultContextKeys.SERVER_KEY).map(v -> v.equals("factions")).orElse(false)
//                    )
//                    .mapToInt(ChatMetaNode::getPriority)
  //                  .max()
    //                .orElse(0);

            logger.info(userGroups.toString());
            //logger.info("filter: " + maxWeight);

            for (Team t : teams) {

                logger.info("i am triying :" + t.getName() + t.getPrefix());
                if (userGroups.get(0).equals(t.getName())) {
                    t.addMember(p.getTeamRepresentation());
                    break;
                }

                continue;
            }

            p.setScoreboard(sb);
        }

    }

    @Listener
    public void started(GameStartedServerEvent e) {
//        Task.builder()
//                .intervalTicks(1)
//                .delayTicks(1)
//                .name("badge-S-PrefixAssigner")
//                .execute(this::assignPrefixes)
//                .submit(this);

        eb.subscribe(LogPublishEvent.class, se -> {
            updateScoreboard();
        });
    }

    private void assignPrefixes() {
//        for (Player p : game.getServer().getOnlinePlayers()) {
//            Optional<Config.Prefix> prefix_
//                    = config.prefixes.stream().filter(
//                            pfx -> p.hasPermission("badge.prefix." + pfx.name.replace('.', '_'))
//                    ).findFirst();
//            if (prefix_.isPresent()) {
//                getScoreboard(p).getTeam(prefix_.get().name).get().addMember(p.getTeamRepresentation());
//            } else {
//                getScoreboard(p).getMemberTeam(p.getTeamRepresentation()).ifPresent(t -> t.removeMember(p.getTeamRepresentation()));
//            }
//        }
        //logger.info("loppingsss");
    }

    @Listener
    public void join(ClientConnectionEvent.Join e) {
        //e.getTargetEntity().setScoreboard(getScoreboard(e.getTargetEntity()));
        //logger.info(getScoreboard().toString());

        updateScoreboard();

    }

    private ConfigurationNode loadDefault() throws IOException {
        return HoconConfigurationLoader.builder().setURL(game.getAssetManager().getAsset(this, "default.conf").get().getUrl()).build().load(loader.getDefaultOptions());
    }

    private void mapDefault() {
        try {
            config = loadDefault().getValue(Config.type);
        } catch (IOException | ObjectMappingException ex) {
            logger.error("Could not load internal config! Disabling plugin.");
            game.getEventManager().unregisterPluginListeners(this);
        }
    }

}
