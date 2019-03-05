package bt.speech.tts;

/**
 * @author &#8904
 *
 */
public enum TTSVoice
{
    CMU_SLT_HSMM("cmu-slt-hsmm"),
    DFKI_SPIKE_HSMM("dfki-spike-hsmm");

    private String voiceName;

    TTSVoice(String voiceName)
    {
        this.voiceName = voiceName;
    }

    public String getVoiceName()
    {
        return this.voiceName;
    }
}
