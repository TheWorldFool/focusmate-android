package cn.local.focusmate;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** Synthesised focus noise and completion chime; no bundled audio assets needed. */
public final class Sound {
  private Sound() { }

  private static volatile boolean running;
  private static volatile String currentType;
  private static volatile int generation;

  public static synchronized void startNoise(String type) {
    if (running && type.equals(currentType)) return;
    stopNoise();
    final int gen = ++generation;
    running = true;
    currentType = type;
    Thread thread = new Thread(() -> {
      AudioTrack track = null;
      try {
        int rate = 22050;
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(8192, min * 2);
        track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();
        short[] samples = new short[2048];
        Random random = new Random();
        double brown = 0;
        double rain = 0;
        double time = 0;
        track.setVolume(0.16f);
        track.play();
        while (running && generation == gen) {
          for (int i = 0; i < samples.length; i++) {
            double white = random.nextDouble() * 2 - 1;
            double value;
            if ("white".equals(type)) {
              value = white * 0.45;
            } else if ("rain".equals(type)) {
              rain = rain * 0.82 + white * 0.18;
              value = rain * 1.5;
              if (random.nextInt(9000) == 0) value += (random.nextDouble() * 2 - 1) * 0.5;
            } else if ("waves".equals(type)) {
              brown = (brown + white * 0.02) / 1.02;
              double env = 0.28 + 0.72 * Math.pow(Math.sin(time * 0.42), 2);
              value = brown * 3.4 * env;
            } else {
              brown = (brown + white * 0.02) / 1.02;
              value = brown * 3.4;
            }
            time += 1.0 / rate;
            if (value > 1) value = 1;
            if (value < -1) value = -1;
            samples[i] = (short) (value * 12000);
          }
          track.write(samples, 0, samples.length);
        }
      } catch (Exception ignored) {
      } finally {
        if (track != null) {
          try { track.stop(); } catch (Exception ignored) { }
          try { track.release(); } catch (Exception ignored) { }
        }
      }
    }, "focus-noise");
    thread.start();
  }

  public static synchronized void stopNoise() {
    running = false;
    currentType = null;
    generation++;
  }

  public static void chime() {
    Thread thread = new Thread(() -> {
      AudioTrack track = null;
      try {
        int rate = 22050;
        int length = rate;
        short[] data = new short[length];
        for (int i = 0; i < length; i++) {
          double t = i / (double) rate;
          double v = 0.32 * Math.sin(2 * Math.PI * 880 * t) * Math.exp(-4.2 * t);
          if (t >= 0.16) {
            double u = t - 0.16;
            v += 0.28 * Math.sin(2 * Math.PI * 1318.5 * u) * Math.exp(-5.5 * u);
          }
          data[i] = (short) (v * 15000);
        }
        track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.length * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build();
        track.write(data, 0, data.length);
        track.play();
        Thread.sleep(950);
      } catch (Exception ignored) {
      } finally {
        if (track != null) {
          try { track.stop(); } catch (Exception ignored) { }
          try { track.release(); } catch (Exception ignored) { }
        }
      }
    }, "focus-chime");
    thread.start();
  }
}
