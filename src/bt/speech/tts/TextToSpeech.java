package bt.speech.tts;

import javax.sound.sampled.AudioInputStream;

import bt.scheduler.Threads;
import marytts.MaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.signalproc.effects.VolumeEffect;
import marytts.util.data.audio.AudioPlayer;

/**
 * @author &#8904
 *
 */
public final class TextToSpeech
{
    private static MaryInterface marytts;
    private static boolean playSynchronized = true;

    static
    {
        try
        {
            marytts = new BtLocalMaryInterface();
            setVoice(TTSVoice.DFKI_SPIKE_HSMM);
        }
        catch (MaryConfigurationException e)
        {
            e.printStackTrace();
        }
    }

    public static void setVolume(float volume)
    {
        VolumeEffect volumeEffect = new VolumeEffect();
        volumeEffect.setParams("amount:" + Float.toString(volume));
        marytts.setAudioEffects(volumeEffect.getFullEffectAsString());
    }

    public synchronized static void setVoice(TTSVoice voice)
    {
        marytts.setVoice(voice.getVoiceName());
    }

    public static void setPlaySynchronized(boolean playSynchronized)
    {
        TextToSpeech.playSynchronized = playSynchronized;
    }

    public static AudioInputStream getAudioInputStream(String text)
    {
        try
        {
            return marytts.generateAudio(text);
        }
        catch (SynthesisException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public static void playAudioOf(AudioInputStream audioStream)
    {
        Threads.get().executeCached(() ->
        {
            synchronized (TextToSpeech.class)
            {
                AudioPlayer ap = new AudioPlayer(audioStream);
                ap.start();

                if (playSynchronized)
                {
                    try
                    {
                        ap.join();
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static void playAudioOf(String text)
    {
        playAudioOf(getAudioInputStream(text));
    }

    public static synchronized void setup()
    {
        System.out.println("Setting up TextToSpeech components.");
        AudioPlayer ap = new AudioPlayer(getAudioInputStream("."));
        ap.start();
    }
}