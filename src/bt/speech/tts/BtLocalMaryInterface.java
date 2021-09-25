package bt.speech.tts;

import marytts.MaryInterface;
import marytts.config.LanguageConfig;
import marytts.config.MaryConfig;
import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.modules.synthesis.Voice;
import marytts.server.Request;
import org.w3c.dom.Document;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public class BtLocalMaryInterface implements MaryInterface
{
    private MaryDataType inputType;
    private MaryDataType outputType;
    private Locale locale;
    private Voice voice;
    private AudioFileFormat audioFileFormat;
    private String effects;
    private String style;
    private String outputTypeParams;
    private boolean isStreaming;

    public BtLocalMaryInterface() throws MaryConfigurationException {
        try {
            BtMaryRuntimeUtils.ensureMaryStarted();
        } catch (Exception var2) {
            throw new MaryConfigurationException("Cannot start MARY server", var2);
        }

        this.init();
    }

    protected void init() {
        this.setReasonableDefaults();
    }

    protected void setReasonableDefaults() {
        this.inputType = MaryDataType.TEXT;
        this.outputType = MaryDataType.AUDIO;
        this.locale = Locale.US;
        this.voice = Voice.getDefaultVoice(this.locale);
        this.setAudioFileFormatForVoice();
        this.effects = null;
        this.style = null;
        this.outputTypeParams = null;
        this.isStreaming = false;
    }

    private void setAudioFileFormatForVoice() {
        if (this.voice != null) {
            AudioFormat af = this.voice.dbAudioFormat();
            this.audioFileFormat = new AudioFileFormat(AudioFileFormat.Type.WAVE, af, -1);
        } else {
            this.audioFileFormat = null;
        }

    }

    public void setInputType(String newInputType) throws IllegalArgumentException {
        this.inputType = MaryDataType.get(newInputType);
        if (this.inputType == null) {
            throw new IllegalArgumentException("No such type: " + newInputType);
        } else if (!this.inputType.isInputType()) {
            throw new IllegalArgumentException("Not an input type: " + newInputType);
        }
    }

    public String getInputType() {
        return this.inputType.name();
    }

    public void setOutputType(String newOutputType) throws IllegalArgumentException {
        this.outputType = MaryDataType.get(newOutputType);
        if (this.outputType == null) {
            throw new IllegalArgumentException("No such type: " + newOutputType);
        } else if (!this.outputType.isOutputType()) {
            throw new IllegalArgumentException("Not an output type: " + newOutputType);
        }
    }

    public String getOutputType() {
        return this.outputType.name();
    }

    public void setLocale(Locale newLocale) throws IllegalArgumentException {
        if (MaryConfig.getLanguageConfig(newLocale) == null) {
            throw new IllegalArgumentException("Unsupported locale: " + newLocale);
        } else {
            this.locale = newLocale;
            this.voice = Voice.getDefaultVoice(this.locale);
            this.setAudioFileFormatForVoice();
        }
    }

    public Locale getLocale() {
        return this.locale;
    }

    public void setVoice(String voiceName) throws IllegalArgumentException {
        this.voice = Voice.getVoice(voiceName);
        if (this.voice == null) {
            throw new IllegalArgumentException("No such voice: " + voiceName);
        } else {
            this.locale = this.voice.getLocale();
            this.setAudioFileFormatForVoice();
        }
    }

    public String getVoice() {
        return this.voice == null ? null : this.voice.getName();
    }

    public void setAudioEffects(String audioEffects) {
        this.effects = audioEffects;
    }

    public String getAudioEffects() {
        return this.effects;
    }

    public void setStyle(String newStyle) {
        this.style = newStyle;
    }

    public String getStyle() {
        return this.style;
    }

    public void setOutputTypeParams(String params) {
        this.outputTypeParams = params;
    }

    public String getOutputTypeParams() {
        return this.outputTypeParams;
    }

    public void setStreamingAudio(boolean newIsStreaming) {
        this.isStreaming = newIsStreaming;
    }

    public boolean isStreamingAudio() {
        return this.isStreaming;
    }

    public String generateText(String text) throws SynthesisException
    {
        this.verifyInputTypeIsText();
        this.verifyOutputTypeIsText();
        MaryData in = this.getMaryDataFromText(text);
        MaryData out = this.process(in);
        return out.getPlainText();
    }

    public String generateText(Document doc) throws SynthesisException {
        this.verifyInputTypeIsXML();
        this.verifyOutputTypeIsText();
        MaryData in = this.getMaryDataFromXML(doc);
        MaryData out = this.process(in);
        return out.getPlainText();
    }

    public Document generateXML(String text) throws SynthesisException {
        this.verifyInputTypeIsText();
        this.verifyOutputTypeIsXML();
        MaryData in = this.getMaryDataFromText(text);
        MaryData out = this.process(in);
        return out.getDocument();
    }

    public Document generateXML(Document doc) throws SynthesisException {
        this.verifyInputTypeIsXML();
        this.verifyOutputTypeIsXML();
        MaryData in = this.getMaryDataFromXML(doc);
        MaryData out = this.process(in);
        return out.getDocument();
    }

    public AudioInputStream generateAudio(String text) throws SynthesisException {
        this.verifyInputTypeIsText();
        this.verifyOutputTypeIsAudio();
        this.verifyVoiceIsAvailableForLocale();
        MaryData in = this.getMaryDataFromText(text);
        MaryData out = this.process(in);
        return out.getAudio();
    }

    public AudioInputStream generateAudio(Document doc) throws SynthesisException {
        this.verifyInputTypeIsXML();
        this.verifyOutputTypeIsAudio();
        this.verifyVoiceIsAvailableForLocale();
        MaryData in = this.getMaryDataFromXML(doc);
        MaryData out = this.process(in);
        return out.getAudio();
    }

    private void verifyOutputTypeIsXML() {
        if (!this.outputType.isXMLType()) {
            throw new IllegalArgumentException("Cannot provide XML output for non-XML-based output type " + this.outputType);
        }
    }

    private void verifyInputTypeIsXML() {
        if (!this.inputType.isXMLType()) {
            throw new IllegalArgumentException("Cannot provide XML input for non-XML-based input type " + this.inputType);
        }
    }

    private void verifyInputTypeIsText() {
        if (this.inputType.isXMLType()) {
            throw new IllegalArgumentException("Cannot provide plain-text input for XML-based input type " + this.inputType);
        }
    }

    private void verifyOutputTypeIsAudio() {
        if (!this.outputType.equals(MaryDataType.AUDIO)) {
            throw new IllegalArgumentException("Cannot provide audio output for non-audio output type " + this.outputType);
        }
    }

    private void verifyOutputTypeIsText() {
        if (this.outputType.isXMLType() || !this.outputType.isTextType()) {
            throw new IllegalArgumentException("Cannot provide text output for non-text output type " + this.outputType);
        }
    }

    private void verifyVoiceIsAvailableForLocale() {
        if (this.outputType.equals(MaryDataType.AUDIO) && this.getAvailableVoices(this.locale).isEmpty()) {
            throw new IllegalArgumentException("No voice is available for Locale: " + this.locale);
        }
    }

    private MaryData getMaryDataFromText(String text) throws SynthesisException {
        MaryData in = new MaryData(this.inputType, this.locale);

        try {
            in.setData(text);
            return in;
        } catch (Exception var4) {
            throw new SynthesisException(var4);
        }
    }

    private MaryData getMaryDataFromXML(Document doc) throws SynthesisException {
        MaryData in = new MaryData(this.inputType, this.locale);

        try {
            in.setDocument(doc);
            return in;
        } catch (Exception var4) {
            throw new SynthesisException(var4);
        }
    }

    private MaryData process(MaryData in) throws SynthesisException {
        Request r = new Request(this.inputType, this.outputType, this.locale, this.voice, this.effects, this.style, 1, this.audioFileFormat, this.isStreaming, this.outputTypeParams);
        r.setInputData(in);

        try {
            r.process();
        } catch (Exception var4) {
            throw new SynthesisException("cannot process", var4);
        }

        return r.getOutputData();
    }

    public Set<String> getAvailableVoices() {
        Set<String> voices = new HashSet();
        Iterator var3 = Voice.getAvailableVoices().iterator();

        while(var3.hasNext()) {
            Voice v = (Voice)var3.next();
            voices.add(v.getName());
        }

        return voices;
    }

    public Set<String> getAvailableVoices(Locale aLocale) {
        Set<String> voices = new HashSet();
        Iterator var4 = Voice.getAvailableVoices(aLocale).iterator();

        while(var4.hasNext()) {
            Voice v = (Voice)var4.next();
            voices.add(v.getName());
        }

        return voices;
    }

    public Set<Locale> getAvailableLocales() {
        Set<Locale> locales = new HashSet();
        Iterator var3 = MaryConfig.getLanguageConfigs().iterator();

        while(var3.hasNext()) {
            LanguageConfig lc = (LanguageConfig)var3.next();
            locales.addAll(lc.getLocales());
        }

        return locales;
    }

    public Set<String> getAvailableInputTypes() {
        return new HashSet(MaryDataType.getInputTypeStrings());
    }

    public Set<String> getAvailableOutputTypes() {
        return new HashSet(MaryDataType.getOutputTypeStrings());
    }

    public boolean isTextType(String dataType) {
        MaryDataType d = MaryDataType.get(dataType);
        if (d != null) {
            return d.isTextType() && !d.isXMLType();
        } else {
            return false;
        }
    }

    public boolean isXMLType(String dataType) {
        MaryDataType d = MaryDataType.get(dataType);
        return d != null ? d.isXMLType() : false;
    }

    public boolean isAudioType(String dataType) {
        return "AUDIO".equals(dataType);
    }
}