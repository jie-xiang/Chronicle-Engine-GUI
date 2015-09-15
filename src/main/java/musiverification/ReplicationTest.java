package musiverification;

import musiverification.helpers.*;
import net.openhft.chronicle.bytes.*;
import net.openhft.chronicle.core.*;
import net.openhft.chronicle.core.pool.*;
import net.openhft.chronicle.engine.api.*;
import net.openhft.chronicle.engine.api.map.*;
import net.openhft.chronicle.engine.api.pubsub.*;
import net.openhft.chronicle.engine.api.tree.*;
import net.openhft.chronicle.engine.fs.*;
import net.openhft.chronicle.engine.map.*;
import net.openhft.chronicle.engine.server.*;
import net.openhft.chronicle.engine.tree.*;
import net.openhft.chronicle.network.*;
import net.openhft.chronicle.network.api.session.*;
import net.openhft.chronicle.wire.*;
import net.openhft.lang.thread.*;
import org.easymock.*;
import org.jetbrains.annotations.*;
import org.junit.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Created by Rob Austin
 */

public class ReplicationTest
{

    public static final WireType WIRE_TYPE = WireType.TEXT;
    public static final String NAME = "/ChMaps/test";
    static final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("all-trees-watcher", true));
    public static ServerEndpoint serverEndpoint1;
    public static ServerEndpoint serverEndpoint2;
    public static ServerEndpoint serverEndpoint3;
    private static AssetTree tree3;
    private static AssetTree tree1;
    private static AssetTree tree2;

    @Before
    public void before() throws IOException
    {
        resetTrees(null);
    }

    private void resetTrees(Consumer<AssetTree> applyRulesToAllTrees) throws IOException
    {
//        YamlLogging.clientWrites = true;
//        YamlLogging.clientReads = true;

        //YamlLogging.showServerWrites = true;

        ClassAliasPool.CLASS_ALIASES.addAlias(ChronicleMapGroupFS.class);
        ClassAliasPool.CLASS_ALIASES.addAlias(FilePerKeyGroupFS.class);
        //Delete any files from the last run
        Files.deleteIfExists(Paths.get(OS.TARGET, NAME));

        TCPRegistry.createServerSocketChannelFor("host.port1", "host.port2", "host.port3");

        WireType writeType = WireType.TEXT;
        tree1 = create(1, writeType, applyRulesToAllTrees);
        tree2 = create(2, writeType,applyRulesToAllTrees);
        tree3 = create(3, writeType, applyRulesToAllTrees);

        serverEndpoint1 = new ServerEndpoint("host.port1", tree1, writeType);
        serverEndpoint2 = new ServerEndpoint("host.port2", tree2, writeType);
        serverEndpoint3 = new ServerEndpoint("host.port3", tree3, writeType);
    }

    @After
    public void after()
    {
        if (tree1 != null)
        {
            tree1.close();
        }
        if (tree2 != null)
        {
            tree2.close();
        }
        if (tree3 != null)
        {
            tree3.close();
        }

        if (serverEndpoint1 != null)
        {
            serverEndpoint1.close();
        }
        if (serverEndpoint2 != null)
        {
            serverEndpoint2.close();
        }
        if (serverEndpoint3 != null)
        {
            serverEndpoint3.close();
        }

        TCPRegistry.reset();
        // TODO TCPRegistery.assertAllServersStopped();
        YamlLogging.clientWrites = false;
        YamlLogging.clientReads = false;
    }

    @NotNull
    private static AssetTree create(final int hostId, Function<Bytes, Wire> writeType, Consumer<AssetTree> applyRules)
    {
        AssetTree tree = new VanillaAssetTree((byte) hostId)
                .forTesting()
                .withConfig(resourcesDir() + "/cmkvst", OS.TARGET + "/" + hostId);

        tree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore",
                VanillaMapView::new,
                KeyValueStore.class);
        tree.root().addLeafRule(EngineReplication.class, "Engine replication holder",
                CMap2EngineReplicator::new);
        tree.root().addLeafRule(KeyValueStore.class, "KVS is Chronicle Map", (context, asset) ->
                new ChronicleMapKeyValueStore(context.wireType(writeType).putReturnsNull(false),
                        asset));

        if (applyRules != null)
        {
            applyRules.accept(tree);
        }

        return tree;
    }

    public static String resourcesDir()
    {
        String path = ReplicationTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path == null)
        {
            return ".";
        }
        return new File(path).getParentFile().getParentFile() + "/src/test/resources";
    }

//    public static void registerTextViewofTree(String desc, AssetTree tree) {
//        tree.registerSubscriber("", TopologicalEvent.class, e ->
//                        // give the collection time to be setup.
//                        ses.schedule(() -> handleTreeUpdate(desc, tree, e), 50, TimeUnit.MILLISECONDS)
//        );
//    }
//
//    static void handleTreeUpdate(String desc, AssetTree tree, TopologicalEvent e) {
//        try {
//            System.out.println(desc + " handle " + e);
//            if (e.added()) {
//                System.out.println(desc + " Added a " + e.name() + " under " + e.assetName());
//                String assetFullName = e.fullName();
//                Asset asset = tree.getAsset(assetFullName);
//                if (asset == null) {
//                    System.out.println("\tbut it's not visible.");
//                    return;
//                }
//                ObjectKeyValueStore view = asset.getView(ObjectKeyValueStore.class);
//                if (view == null) {
//                    System.out.println("\t[node]");
//                } else {
//                    long elements = view.longSize();
//                    Class keyType = view.keyType();
//                    Class valueType = view.valueType();
//                    ObjectKVSSubscription objectKVSSubscription = asset.getView(ObjectKVSSubscription.class);
//                    int keySubscriberCount = objectKVSSubscription.keySubscriberCount();
//                    int entrySubscriberCount = objectKVSSubscription.entrySubscriberCount();
//                    int topicSubscriberCount = objectKVSSubscription.topicSubscriberCount();
//                    System.out.println("\t[map]");
//                    System.out.printf("\t%-20s %s%n", "keyType", keyType.getName());
//                    System.out.printf("\t%-20s %s%n", "valueType", valueType.getName());
//                    System.out.printf("\t%-20s %s%n", "size", elements);
//                    System.out.printf("\t%-20s %s%n", "keySubscriberCount", keySubscriberCount);
//                    System.out.printf("\t%-20s %s%n", "entrySubscriberCount", entrySubscriberCount);
//                    System.out.printf("\t%-20s %s%n", "topicSubscriberCount", topicSubscriberCount);
//                }
//            } else {
//                System.out.println(desc + " Removed a " + e.name() + " under " + e.assetName());
//            }
//        } catch (Throwable t) {
//            t.printStackTrace();
//        }
//    }

    @Test
    public void test() throws InterruptedException
    {

        final ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map1);

        final ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map2);

        final ConcurrentMap<String, String> map3 = tree3.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map3);

        map1.put("hello1", "world1");
        map2.put("hello2", "world2");
        map3.put("hello3", "world3");

        for (int i = 1; i <= 50; i++)
        {
            if (map1.size() == 3 && map2.size() == 3 && map3.size() == 3)
            {
                break;
            }
            Jvm.pause(200);
        }


        for (Map m : new Map[]{map1, map2, map3})
        {
            Assert.assertEquals("world1", m.get("hello1"));
            Assert.assertEquals("world2", m.get("hello2"));
            Assert.assertEquals("world3", m.get("hello3"));
            Assert.assertEquals(3, m.size());
        }

    }

    /**
     * Test that events are only received once and in order
     *
     * @throws InterruptedException
     * @throws InvalidSubscriberException
     */
    @Test
    public void testSubscriptionNoOfEvents() throws InterruptedException, InvalidSubscriberException
    {
        final ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map1);

        Subscriber<String> subscriberMock = EasyMock.createStrictMock(Subscriber.class);

        tree1.registerSubscriber(NAME + "?bootstrap=false", String.class, subscriberMock);

        final ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME + "?bootstrap=false", String.class, String
                .class);
        Assert.assertNotNull(map2);

        subscriberMock.onMessage("hello1");
        subscriberMock.onMessage("hello2");

        EasyMock.replay(subscriberMock);

        map1.put("hello1", "world1");
        map2.put("hello2", "world2");

        for (int i = 1; i <= 50; i++)
        {
            if (map1.size() == 2 && map2.size() == 2)
            {
                break;
            }
            Jvm.pause(200);
        }

        EasyMock.verify(subscriberMock);

        for (Map m : new Map[]{map1, map2})
        {
            Assert.assertEquals("world1", m.get("hello1"));
            Assert.assertEquals("world2", m.get("hello2"));
            Assert.assertEquals(2, m.size());
        }
    }

    /**
     * Test that session details are set on all replication method calls.
     *
     * @throws InterruptedException
     * @throws InvalidSubscriberException
     */
    @Test
    public void testSessionDetailsSet() throws InterruptedException, InvalidSubscriberException, IOException
    {
        resetTrees(this::setSessionDetailsAndTestWrapperOnTree);

        final ConcurrentMap<String, String> map1 = tree1.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map1);

        Subscriber<String> subscriberMock = EasyMock.createMock(Subscriber.class);

        tree1.registerSubscriber(NAME + "?bootstrap=false", String.class, subscriberMock);

        final ConcurrentMap<String, String> map2 = tree2.acquireMap(NAME, String.class, String
                .class);
        Assert.assertNotNull(map2);

        subscriberMock.onMessage("hello1");
        subscriberMock.onMessage("hello2");
        subscriberMock.onMessage("hello2"); //TODO hack due to multiple events bug

        EasyMock.replay(subscriberMock);

        map1.put("hello1", "world1");
        map2.put("hello2", "world2");

        for (int i = 1; i <= 50; i++)
        {
            if (map1.size() == 2 && map2.size() == 2)
            {
                break;
            }
            Jvm.pause(200);
        }

        EasyMock.verify(subscriberMock);
        EasyMock.reset(subscriberMock); //HACK for endOfSubscription event

        for (Map m : new Map[]{map1, map2})
        {
            Assert.assertEquals("world1", m.get("hello1"));
            Assert.assertEquals("world2", m.get("hello2"));
            Assert.assertEquals(2, m.size());
        }
    }

    private void setSessionDetailsAndTestWrapperOnTree(AssetTree assetTree)
    {
        SessionProvider sessionProvider = assetTree.root().acquireView(SessionProvider.class);
        VanillaSessionDetails vanillaSessionDetails = VanillaSessionDetails.of("testUser", null);
        sessionProvider.set(vanillaSessionDetails);

        assetTree.root().addWrappingRule(ObjectKVSSubscription.class, "Check session details subscription",
                CheckSessionDetailsSubscription::new, VanillaKVSSubscription.class);

        assetTree.root().addLeafRule(VanillaKVSSubscription.class, "Chronicle vanilla subscription", VanillaKVSSubscription::new);
    }

}

