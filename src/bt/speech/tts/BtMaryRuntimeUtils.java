package bt.speech.tts;

import marytts.Version;
import marytts.config.LanguageConfig;
import marytts.config.MaryConfig;
import marytts.datatypes.MaryDataType;
import marytts.exceptions.MaryConfigurationException;
import marytts.fst.FSTLookup;
import marytts.htsengine.HMMVoice;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.modules.synthesis.Voice;
import marytts.server.MaryProperties;
import marytts.signalproc.effects.AudioEffect;
import marytts.signalproc.effects.AudioEffects;
import marytts.unitselection.UnitSelectionVoice;
import marytts.unitselection.interpolation.InterpolatingVoice;
import marytts.util.MaryUtils;
import marytts.util.data.audio.AudioDestination;
import marytts.util.data.audio.MaryAudioUtils;
import marytts.util.dom.MaryDomUtils;
import marytts.util.string.StringUtils;
import marytts.vocalizations.VocalizationSynthesizer;
import org.w3c.dom.Element;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;

public class BtMaryRuntimeUtils
{
    private static long lowMemoryThreshold = -1L;
    private static final String audiostoreProperty = MaryProperties.getProperty("synthesis.audiostore", "ram");

    public BtMaryRuntimeUtils() {
    }

    public static void ensureMaryStarted() throws Exception {
        synchronized(MaryConfig.getMainConfig()) {
            if (BtMary.currentState() == 0) {
                BtMary.startup();
            }

        }
    }

    public static Object instantiateObject(String objectInitInfo) throws MaryConfigurationException
    {
        Object obj = null;
        String[] args = null;
        String className = null;

        try {
            if (objectInitInfo.contains("(")) {
                int firstOpenBracket = objectInitInfo.indexOf(40);
                className = objectInitInfo.substring(0, firstOpenBracket);
                int lastCloseBracket = objectInitInfo.lastIndexOf(41);
                args = objectInitInfo.substring(firstOpenBracket + 1, lastCloseBracket).split(",");

                for(int i = 0; i < args.length; ++i) {
                    if (args[i].startsWith("$")) {
                        args[i] = MaryProperties.getProperty(args[i].substring(1));
                    }

                    args[i] = args[i].trim();
                }
            } else {
                className = objectInitInfo;
            }

            Class<? extends Object> theClass = Class.forName(className).asSubclass(Object.class);
            if (args != null) {
                Class[] constructorArgTypes = new Class[args.length];
                Object[] constructorArgs = new Object[args.length];

                for(int i = 0; i < args.length; ++i) {
                    constructorArgTypes[i] = String.class;
                    constructorArgs[i] = args[i];
                }

                Constructor<? extends Object> constructor = theClass.getConstructor(constructorArgTypes);
                obj = constructor.newInstance(constructorArgs);
            } else {
                obj = theClass.newInstance();
            }

            return obj;
        } catch (Exception var8) {
            throw new MaryConfigurationException("Cannot instantiate object from '" + objectInitInfo + "': " + MaryUtils.getFirstMeaningfulMessage(var8), var8);
        }
    }

    public static boolean lowMemoryCondition() {
        return MaryUtils.availableMemory() < lowMemoryThreshold();
    }

    public static boolean veryLowMemoryCondition() {
        return MaryUtils.availableMemory() < lowMemoryThreshold() / 2L;
    }

    private static long lowMemoryThreshold() {
        if (lowMemoryThreshold < 0L) {
            lowMemoryThreshold = (long)MaryProperties.getInteger("mary.lowmemory", 10000000);
        }

        return lowMemoryThreshold;
    }

    public static String getAudioFileFormatTypes() {
        StringBuilder output = new StringBuilder();
        AudioFileFormat.Type[] audioTypes = AudioSystem.getAudioFileTypes();

        for(int t = 0; t < audioTypes.length; ++t) {
            AudioFileFormat.Type audioType = audioTypes[t];
            String typeName = audioType.toString();
            boolean isSupported = true;
            if (typeName.equals("MP3")) {
                isSupported = canCreateMP3();
            } else if (typeName.equals("Vorbis")) {
                isSupported = canCreateOgg();
            }

            audioType = MaryAudioUtils.getAudioFileFormatType(typeName);
            if (audioType == null) {
                isSupported = false;
            }

            if (isSupported && AudioSystem.isFileTypeSupported(audioType)) {
                output.append(typeName).append("_FILE\n");
                if (typeName.equals("MP3") || typeName.equals("Vorbis")) {
                    output.append(typeName).append("_STREAM\n");
                }
            }
        }

        return output.toString();
    }

    public static boolean canCreateMP3() {
        return AudioSystem.isConversionSupported(getMP3AudioFormat(), Voice.AF22050);
    }

    public static AudioFormat getMP3AudioFormat() {
        return new AudioFormat(new AudioFormat.Encoding("MPEG1L3"), -1.0F, -1, 1, -1, -1.0F, false);
    }

    public static boolean canCreateOgg() {
        return AudioSystem.isConversionSupported(getOggAudioFormat(), Voice.AF22050);
    }

    public static AudioFormat getOggAudioFormat() {
        return new AudioFormat(new AudioFormat.Encoding("VORBIS"), -1.0F, -1, 1, -1, -1.0F, false);
    }

    public static AllophoneSet determineAllophoneSet(Element e) throws MaryConfigurationException {
        AllophoneSet allophoneSet = null;
        Element voice = (Element)MaryDomUtils.getAncestor(e, "voice");
        Voice maryVoice = Voice.getVoice(voice);
        Locale locale;
        if (maryVoice == null) {
            locale = MaryUtils.string2locale(e.getOwnerDocument().getDocumentElement().getAttribute("xml:lang"));
            maryVoice = Voice.getDefaultVoice(locale);
        }

        if (maryVoice != null) {
            allophoneSet = maryVoice.getAllophoneSet();
        } else {
            locale = MaryUtils.string2locale(e.getOwnerDocument().getDocumentElement().getAttribute("xml:lang"));
            allophoneSet = determineAllophoneSet(locale);
        }

        return allophoneSet;
    }

    public static AllophoneSet determineAllophoneSet(Locale locale) throws MaryConfigurationException {
        AllophoneSet allophoneSet = null;
        String propertyPrefix = MaryProperties.localePrefix(locale);
        if (propertyPrefix != null) {
            String propertyName = propertyPrefix + ".allophoneset";
            allophoneSet = needAllophoneSet(propertyName);
        }

        return allophoneSet;
    }

    public static AudioDestination createAudioDestination() throws IOException
    {
        boolean ram = false;
        if (audiostoreProperty.equals("ram")) {
            ram = true;
        } else if (audiostoreProperty.equals("file")) {
            ram = false;
        } else if (lowMemoryCondition()) {
            ram = false;
        } else {
            ram = true;
        }

        return new AudioDestination(ram);
    }

    public static AllophoneSet needAllophoneSet(String propertyName) throws MaryConfigurationException {
        String propertyValue = MaryProperties.getProperty(propertyName);
        if (propertyValue == null) {
            throw new MaryConfigurationException("No such property: " + propertyName);
        } else if (AllophoneSet.hasAllophoneSet(propertyValue)) {
            return AllophoneSet.getAllophoneSetById(propertyValue);
        } else {
            InputStream alloStream;
            try {
                alloStream = MaryProperties.needStream(propertyName);
            } catch (FileNotFoundException var4) {
                throw new MaryConfigurationException("Cannot open allophone stream for property " + propertyName, var4);
            }

            assert alloStream != null;

            return AllophoneSet.getAllophoneSet(alloStream, propertyValue);
        }
    }

    public static String[] checkLexicon(String propertyName, String token) throws IOException, MaryConfigurationException {
        String lexiconProperty = propertyName + ".lexicon";
        InputStream lexiconStream = MaryProperties.needStream(lexiconProperty);
        FSTLookup lexicon = new FSTLookup(lexiconStream, lexiconProperty);
        return lexicon.lookup(token.toLowerCase());
    }

    public static String getMaryVersion() {
        String output = "Mary TTS server " + Version.specificationVersion() + " (impl. " + Version.implementationVersion() + ")";
        return output;
    }

    public static String getDataTypes() {
        String output = "";
        List<MaryDataType> allTypes = MaryDataType.getDataTypes();

        for(Iterator var3 = allTypes.iterator(); var3.hasNext(); output = output + System.getProperty("line.separator")) {
            MaryDataType t = (MaryDataType)var3.next();
            output = output + t.name();
            if (t.isInputType()) {
                output = output + " INPUT";
            }

            if (t.isOutputType()) {
                output = output + " OUTPUT";
            }
        }

        return output;
    }

    public static String getLocales() {
        StringBuilder out = new StringBuilder();
        Iterator var2 = MaryConfig.getLanguageConfigs().iterator();

        while(var2.hasNext()) {
            LanguageConfig conf = (LanguageConfig)var2.next();
            Iterator var4 = conf.getLocales().iterator();

            while(var4.hasNext()) {
                Locale locale = (Locale)var4.next();
                out.append(locale).append('\n');
            }
        }

        return out.toString();
    }

    public static String getVoices() {
        String output = "";
        Collection<Voice> voices = Voice.getAvailableVoices();
        Iterator it = voices.iterator();

        while(it.hasNext()) {
            Voice v = (Voice)it.next();
            if (!(v instanceof InterpolatingVoice)) {
                if (v instanceof UnitSelectionVoice) {
                    output = output + v.getName() + " " + v.getLocale() + " " + v.gender().toString() + " " + "unitselection" + " " + ((UnitSelectionVoice)v).getDomain() + System.getProperty("line.separator");
                } else if (v instanceof HMMVoice) {
                    output = output + v.getName() + " " + v.getLocale() + " " + v.gender().toString() + " " + "hmm" + System.getProperty("line.separator");
                } else {
                    output = output + v.getName() + " " + v.getLocale() + " " + v.gender().toString() + " " + "other" + System.getProperty("line.separator");
                }
            }
        }

        return output;
    }

    public static String getDefaultVoiceName() {
        String defaultVoiceName = "";
        String allVoices = getVoices();
        if (allVoices != null && allVoices.length() > 0) {
            StringTokenizer tt = new StringTokenizer(allVoices, System.getProperty("line.separator"));
            if (tt.hasMoreTokens()) {
                defaultVoiceName = tt.nextToken();
                StringTokenizer tt2 = new StringTokenizer(defaultVoiceName, " ");
                if (tt2.hasMoreTokens()) {
                    defaultVoiceName = tt2.nextToken();
                }
            }
        }

        return defaultVoiceName;
    }

    public static String getExampleText(String datatype, Locale locale) {
        MaryDataType type = MaryDataType.get(datatype);
        String exampleText = type.exampleText(locale);
        return exampleText != null ? exampleText.trim() + System.getProperty("line.separator") : "";
    }

    public static Vector<String> getDefaultVoiceExampleTexts() {
        String defaultVoiceName = getDefaultVoiceName();
        Vector<String> defaultVoiceExampleTexts = null;
        defaultVoiceExampleTexts = StringUtils.processVoiceExampleText(getVoiceExampleText(defaultVoiceName));
        if (defaultVoiceExampleTexts == null) {
            String str = getExampleText("TEXT", Voice.getVoice(defaultVoiceName).getLocale());
            if (str != null && str.length() > 0) {
                defaultVoiceExampleTexts = new Vector();
                defaultVoiceExampleTexts.add(str);
            }
        }

        return defaultVoiceExampleTexts;
    }

    public static String getVoiceExampleText(String voiceName) {
        Voice v = Voice.getVoice(voiceName);
        return v instanceof UnitSelectionVoice ? ((UnitSelectionVoice)v).getExampleText() : "";
    }

    public static String getVocalizations(String voiceName) {
        Voice v = Voice.getVoice(voiceName);
        if (v != null && v.hasVocalizationSupport()) {
            VocalizationSynthesizer vs = v.getVocalizationSynthesizer();

            assert vs != null;

            String[] vocalizations = vs.listAvailableVocalizations();

            assert vocalizations != null;

            return StringUtils.toString(vocalizations);
        } else {
            return "";
        }
    }

    public static String getStyles(String voiceName) {
        Voice v = Voice.getVoice(voiceName);
        String[] styles = null;
        if (v != null) {
            styles = v.getStyles();
        }

        return styles != null ? StringUtils.toString(styles) : "";
    }

    public static String getDefaultAudioEffects() {
        StringBuilder sb = new StringBuilder();
        Iterator var2 = AudioEffects.getEffects().iterator();

        while(var2.hasNext()) {
            AudioEffect effect = (AudioEffect)var2.next();
            sb.append(effect.getName()).append(" ").append(effect.getExampleParameters()).append("\n");
        }

        return sb.toString();
    }

    public static String getAudioEffectDefaultParam(String effectName) {
        AudioEffect effect = AudioEffects.getEffect(effectName);
        return effect == null ? "" : effect.getExampleParameters().trim();
    }

    public static String getFullAudioEffect(String effectName, String currentEffectParams) {
        AudioEffect effect = AudioEffects.getEffect(effectName);
        if (effect == null) {
            return "";
        } else {
            effect.setParams(currentEffectParams);
            return effect.getFullEffectAsString();
        }
    }

    public static String getAudioEffectHelpText(String effectName) {
        AudioEffect effect = AudioEffects.getEffect(effectName);
        return effect == null ? "" : effect.getHelpText().trim();
    }

    public static String isHmmAudioEffect(String effectName) {
        AudioEffect effect = AudioEffects.getEffect(effectName);
        if (effect == null) {
            return "";
        } else {
            return effect.isHMMEffect() ? "yes" : "no";
        }
    }
}
