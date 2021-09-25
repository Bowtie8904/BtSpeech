package bt.speech.tts;

import marytts.Version;
import marytts.datatypes.MaryDataType;
import marytts.exceptions.MaryConfigurationException;
import marytts.features.FeatureProcessorManager;
import marytts.features.FeatureRegistry;
import marytts.modules.MaryModule;
import marytts.modules.ModuleRegistry;
import marytts.modules.Synthesis;
import marytts.modules.synthesis.Voice;
import marytts.server.EnvironmentChecks;
import marytts.server.Mary;
import marytts.server.MaryProperties;
import marytts.server.Request;
import marytts.util.MaryCache;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;
import marytts.util.Pair;
import marytts.util.data.audio.MaryAudioUtils;
import marytts.util.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import java.io.*;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.*;

public class BtMary
{
    public static final int STATE_OFF = 0;
    public static final int STATE_STARTING = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_SHUTTING_DOWN = 3;
    private static Logger logger;
    private static int currentState = 0;
    private static boolean jarsAdded = false;

    public BtMary() {
    }

    public static int currentState() {
        return currentState;
    }

    protected static void addJarsToClasspath() throws Exception {
    }

    private static void startModules() throws ClassNotFoundException, InstantiationException, Exception {
        Iterator var1 = MaryProperties.moduleInitInfo().iterator();

        while(var1.hasNext()) {
            String moduleClassName = (String)var1.next();
            MaryModule m = ModuleRegistry.instantiateModule(moduleClassName);
            ModuleRegistry.registerModule(m, m.getLocale(), (Voice)null);
        }

        ModuleRegistry.setRegistrationComplete();
        List<Pair<MaryModule, Long>> startupTimes = new ArrayList();
        Iterator var11 = ModuleRegistry.getAllModules().iterator();

        while(var11.hasNext()) {
            MaryModule m = (MaryModule)var11.next();
            if ((!MaryProperties.getProperty("server").equals("commandline") || m instanceof Synthesis) && m.getState() == 0) {
                long before = System.currentTimeMillis();

                try {
                    m.startup();
                } catch (Throwable var7) {
                    throw new Exception("Problem starting module " + m.name(), var7);
                }

                long after = System.currentTimeMillis();
                startupTimes.add(new Pair(m, after - before));
            }

            if (MaryProperties.getAutoBoolean("modules.poweronselftest", false)) {
                m.powerOnSelfTest();
            }
        }

        if (startupTimes.size() > 0) {
            Collections.sort(startupTimes, new Comparator<Pair<MaryModule, Long>>() {
                public int compare(Pair<MaryModule, Long> o1, Pair<MaryModule, Long> o2) {
                    return -((Long)o1.getSecond()).compareTo((Long)o2.getSecond());
                }
            });
            logger.debug("Startup times:");
            var11 = startupTimes.iterator();

            while(var11.hasNext()) {
                Pair<MaryModule, Long> p = (Pair)var11.next();
                logger.debug(((MaryModule)p.getFirst()).name() + ": " + p.getSecond() + " ms");
            }
        }

    }

    private static void setupFeatureProcessors() throws Exception {
        Iterator var1 = MaryProperties.getList("featuremanager.classes.list").iterator();

        while(var1.hasNext()) {
            String fpmInitInfo = (String)var1.next();

            try {
                FeatureProcessorManager mgr = (FeatureProcessorManager)MaryRuntimeUtils.instantiateObject(fpmInitInfo);
                Locale locale = mgr.getLocale();
                if (locale != null) {
                    FeatureRegistry.setFeatureProcessorManager(locale, mgr);
                } else {
                    logger.debug("Setting fallback feature processor manager to '" + fpmInitInfo + "'");
                    FeatureRegistry.setFallbackFeatureProcessorManager(mgr);
                }
            } catch (Throwable var4) {
                throw new Exception("Cannot instantiate feature processor manager '" + fpmInitInfo + "'", var4);
            }
        }

    }

    public static void startup() throws Exception {
        startup(true);
    }

    public static void startup(boolean addJarsToClasspath) throws Exception {
        if (currentState != 0) {
            throw new IllegalStateException("Cannot start system: it is not offline");
        } else {
            currentState = 1;
            if (addJarsToClasspath) {
                addJarsToClasspath();
            }

            configureLogging();
            logger.info("Mary starting up...");
            logger.info("Specification version " + Version.specificationVersion());
            logger.info("Implementation version " + Version.implementationVersion());
            logger.info("Running on a Java " + System.getProperty("java.version") + " implementation by " + System.getProperty("java.vendor") + ", on a " + System.getProperty("os.name") + " platform (" + System.getProperty("os.arch") + ", " + System.getProperty("os.version") + ")");
            logger.debug("MARY_BASE: " + MaryProperties.maryBase());
            String[] installedFilenames = (new File(MaryProperties.maryBase() + "/installed")).list();
            int var5;
            if (installedFilenames == null) {
                logger.debug("The installed/ folder does not exist.");
            } else {
                StringBuilder installedMsg = new StringBuilder();
                String[] var6 = installedFilenames;
                var5 = installedFilenames.length;

                for(int var4 = 0; var4 < var5; ++var4) {
                    String filename = var6[var4];
                    if (installedMsg.length() > 0) {
                        installedMsg.append(", ");
                    }

                    installedMsg.append(filename);
                }

                logger.debug("Content of installed/ folder: " + installedMsg);
            }

            String[] confFilenames = (new File(MaryProperties.maryBase() + "/conf")).list();
            if (confFilenames == null) {
                logger.debug("The conf/ folder does not exist.");
            } else {
                StringBuilder confMsg = new StringBuilder();
                String[] var7 = confFilenames;
                int var15 = confFilenames.length;

                for(var5 = 0; var5 < var15; ++var5) {
                    String filename = var7[var5];
                    if (confMsg.length() > 0) {
                        confMsg.append(", ");
                    }

                    confMsg.append(filename);
                }

                logger.debug("Content of conf/ folder: " + confMsg);
            }

            logger.debug("Full dump of system properties:");
            Iterator var14 = (new TreeSet(System.getProperties().keySet())).iterator();

            while(var14.hasNext()) {
                Object key = var14.next();
                logger.debug(key + " = " + System.getProperties().get(key));
            }

            logger.debug("XML libraries used:");
            logger.debug("DocumentBuilderFactory: " + DocumentBuilderFactory.newInstance().getClass());

            try {
                Class<? extends Object> xercesVersion = Class.forName("org.apache.xerces.impl.Version");
                logger.debug(xercesVersion.getMethod("getVersion").invoke((Object)null));
            } catch (Exception var8) {
            }

            logger.debug("TransformerFactory:     " + TransformerFactory.newInstance().getClass());

            // removed because it checks the Java version with a substring
            // after the update to Java 17 the version string is shorter, so the substring call causes an exception
            //EnvironmentChecks.check();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    Mary.shutdown();
                }
            });
            setupFeatureProcessors();
            startModules();
            logger.info("Startup complete.");
            currentState = 2;
        }
    }

    private static void configureLogging() throws MaryConfigurationException, IOException
    {
        if (!MaryUtils.isLog4jConfigured()) {
            Properties logprops = new Properties();
            InputStream propIS = new BufferedInputStream(MaryProperties.needStream("log.config"));
            logprops.load(propIS);
            propIS.close();
            Iterator var3 = logprops.keySet().iterator();

            while(var3.hasNext()) {
                Object key = var3.next();
                String val = (String)logprops.get(key);
                if (val.contains("MARY_BASE")) {
                    String maryBase = MaryProperties.maryBase();
                    if (maryBase.contains("\\")) {
                        maryBase = maryBase.replaceAll("\\\\", "/");
                    }

                    val = val.replaceAll("MARY_BASE", maryBase);
                    logprops.put(key, val);
                }
            }

            String loggerMaryttsKey = "log4j.logger.marytts";
            String loggerMaryttsValue = MaryProperties.getProperty(loggerMaryttsKey);
            if (loggerMaryttsValue != null) {
                logprops.setProperty(loggerMaryttsKey, loggerMaryttsValue);
            }

            PropertyConfigurator.configure(logprops);
        }

        logger = MaryUtils.getLogger("main");
    }

    public static void shutdown() {
        if (currentState != 2) {
            throw new IllegalStateException("MARY system is not running");
        } else {
            currentState = 3;
            logger.info("Shutting down modules...");
            Iterator var1 = ModuleRegistry.getAllModules().iterator();

            while(var1.hasNext()) {
                MaryModule m = (MaryModule)var1.next();
                if (m.getState() == 1) {
                    m.shutdown();
                }
            }

            if (MaryCache.haveCache()) {
                MaryCache cache = MaryCache.getCache();

                try {
                    cache.shutdown();
                } catch (SQLException var2) {
                    logger.warn("Cannot shutdown cache: ", var2);
                }
            }

            logger.info("Shutdown complete.");
            currentState = 0;
        }
    }

    public static void process(String input, String inputTypeName, String outputTypeName, String localeString, String audioTypeName, String voiceName, String style, String effects, String outputTypeParams, OutputStream output) throws Exception {
        if (currentState != 2) {
            throw new IllegalStateException("MARY system is not running");
        } else {
            MaryDataType inputType = MaryDataType.get(inputTypeName);
            MaryDataType outputType = MaryDataType.get(outputTypeName);
            Locale locale = MaryUtils.string2locale(localeString);
            Voice voice = null;
            if (voiceName != null) {
                voice = Voice.getVoice(voiceName);
            }

            AudioFileFormat audioFileFormat = null;
            AudioFileFormat.Type audioType = null;
            if (audioTypeName != null) {
                audioType = MaryAudioUtils.getAudioFileFormatType(audioTypeName);
                AudioFormat audioFormat = null;
                if (audioTypeName.equals("MP3")) {
                    audioFormat = MaryRuntimeUtils.getMP3AudioFormat();
                } else if (audioTypeName.equals("Vorbis")) {
                    audioFormat = MaryRuntimeUtils.getOggAudioFormat();
                } else if (voice != null) {
                    audioFormat = voice.dbAudioFormat();
                } else {
                    audioFormat = Voice.AF22050;
                }

                audioFileFormat = new AudioFileFormat(audioType, audioFormat, -1);
            }

            Request request = new Request(inputType, outputType, locale, voice, effects, style, 1, audioFileFormat, false, outputTypeParams);
            request.setInputData(input);
            request.process();
            request.writeOutputData(output);
        }
    }

    public static void main(final String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        addJarsToClasspath();
        String server = MaryProperties.needProperty("server");
        System.err.print("MARY server " + Version.specificationVersion() + " starting as a ");
        if (server.equals("socket")) {
            System.err.print("socket server...");
        } else if (server.equals("http")) {
            System.err.print("HTTP server...");
        } else {
            System.err.print("command-line application...");
        }

        int localPort = MaryProperties.needInteger("socket.port");
        ServerSocket serverSocket;
        if (!server.equals("commandline")) {
            try {
                serverSocket = new ServerSocket(localPort);
                serverSocket.close();
            } catch (IOException var6) {
                System.err.println("\nPort " + localPort + " already in use!");
                throw var6;
            }
        }

        startup();
        System.err.println(" started in " + (double)(System.currentTimeMillis() - startTime) / 1000.0D + " s on port " + localPort);
        serverSocket = null;
        Runnable main;
        if (server.equals("socket")) {
            main = (Runnable)Class.forName("marytts.server.MaryServer").newInstance();
        } else if (server.equals("http")) {
            main = (Runnable)Class.forName("marytts.server.http.MaryHttpServer").newInstance();
        } else {
            main = new Runnable() {
                public void run() {
                    try {
                        Object inputStream;
                        if (args.length != 0 && !args[0].equals("-")) {
                            inputStream = new FileInputStream(args[0]);
                        } else {
                            inputStream = System.in;
                        }

                        String input = FileUtils.getStreamAsString((InputStream)inputStream, "UTF-8");
                        Mary.process(input, MaryProperties.getProperty("input.type", "TEXT"), MaryProperties.getProperty("output.type", "AUDIO"), MaryProperties.getProperty("locale", "en_US"), MaryProperties.getProperty("audio.type", "WAVE"), MaryProperties.getProperty("voice", (String)null), MaryProperties.getProperty("style", (String)null), MaryProperties.getProperty("effect", (String)null), MaryProperties.getProperty("output.type.params", (String)null), System.out);
                    } catch (Exception var3) {
                        throw new RuntimeException(var3);
                    }
                }
            };
        }

        main.run();
    }
}
