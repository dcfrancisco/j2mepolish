//#condition polish.android
package de.enough.polish.android.media.enough;

import java.io.IOException;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import de.enough.polish.android.helper.ResourceInputStream;
import de.enough.polish.android.media.Control;
import de.enough.polish.android.media.MediaException;
import de.enough.polish.android.media.PlayerListener;
import de.enough.polish.android.media.control.VolumeControl;
import de.enough.polish.android.midlet.MidletBridge;

public class AudioPlaybackPlayer 
extends AbstractPlayer 
implements VolumeControl, MediaPlayer.OnCompletionListener 
{

	private final MediaPlayer mediaPlayer;
	private final AudioManager androidAudioManager;
	private String locator;
	private ResourceInputStream locatorStream;
	private int loopCount = 1;
	private int currentLoop;
	
	private AudioPlaybackPlayer() throws MediaException {
		MidletBridge.instance.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.mediaPlayer = new MediaPlayer();
		this.mediaPlayer.setOnCompletionListener(this);
		this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
		//this.mediaPlayer.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.androidAudioManager = (AudioManager)MidletBridge.instance.getSystemService(Context.AUDIO_SERVICE);
	}
	
	public AudioPlaybackPlayer(String locator) throws MediaException {
		this();
		this.locator = locator; 
		try {
			this.mediaPlayer.setDataSource(locator);
		} catch (Exception e) {
			//#debug error
			System.out.println("Unable to start audio player for " + locator + e);
			throw new MediaException(e.toString());
		}
	}

	public AudioPlaybackPlayer(ResourceInputStream stream) throws MediaException {
		this();
		this.locatorStream = stream;
		try {
			AssetFileDescriptor descriptor = MidletBridge.getInstance().getAssets().openFd( stream.getCleanedResourceUrl() );
			this.mediaPlayer.setDataSource( descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getDeclaredLength() );
			descriptor.close();
		} catch (Exception e) {
			//#debug error
			System.out.println("Unable to initialize player for " + stream.getResourceUrl() + e);
			throw new MediaException(e.toString());
		}
		
	}

	protected void doClose() {
		this.mediaPlayer.release();
	}

	protected void doDeallocate() {
		this.mediaPlayer.reset();
		// now move player into initialized state again:
		try {
			if (this.locatorStream != null) {
				AssetFileDescriptor descriptor = MidletBridge.getInstance().getAssets().openFd( this.locatorStream.getCleanedResourceUrl() );
				this.mediaPlayer.setDataSource( descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getDeclaredLength() );
				descriptor.close();
			} else if (this.locator != null) {
				this.mediaPlayer.setDataSource(this.locator);
			}
		} catch (Exception e) {
			//#debug error
			System.out.println("Unable to deallocate/init player" + e);
		}
	}
	
	public void setLoopCount(int count) {
		this.loopCount = count;
	}

	protected String doGetContentType() {
		throw new RuntimeException("Not supported.");
	}

	protected long doGetMediaTime() {
		return this.mediaPlayer.getCurrentPosition();
	}

	protected void doPrefetch() throws MediaException {
		try {
			this.mediaPlayer.prepare();
		} catch (IllegalStateException e) {
			throw new MediaException(e.getMessage() + ", MIDP State is " + this.meState);
		} catch (IOException e) {
			throw new MediaException(e.getMessage());
		}
	}

	protected void doRealize() throws MediaException {
		// not needed. Done with setDataSource.
	}

	protected long doSetMediaTime(long time) throws MediaException {
		throw new MediaException("Not supported.");
	}

	protected void doStart() {
		this.currentLoop = this.loopCount;
		this.mediaPlayer.start();
	}

	protected void doStop() throws MediaException {
		this.mediaPlayer.stop();
	}

	public int getLevel() {
		int androidMaxVolume = this.androidAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		int androidVolume = this.androidAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		int meLevel = (int) (100f / androidMaxVolume * androidVolume);
		return meLevel;
	}

	public boolean isMuted() {
		int streamVolume = this.androidAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		return streamVolume <= 0;
	}

	public int setLevel(int level) {
		if (level < 0) {
			level = 0;
		}
		if (level > 100) {
			level = 100;
		}
		int androidMaxVolume = this.androidAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		int androidVolume = (int)(androidMaxVolume / 100f * level);
		this.androidAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, androidVolume, AudioManager.FLAG_SHOW_UI);
		return level;
	}

	public void setMute(boolean mute) {
		this.androidAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
	}

	public Control getControl(String controlType) {
		if("VolumeControl".equals(controlType)) {
			return this;
		}
		return null;
	}

	public Control[] getControls() {
		return new Control[] {this};
	}

	protected void doStarted() {
		// not required
	}

	/*
	 * (non-Javadoc)
	 * @see android.media.MediaPlayer.OnCompletionListener#onCompletion(android.media.MediaPlayer)
	 */
	public void onCompletion(MediaPlayer mp) {
		int loop = this.currentLoop;
		if (loop == -1) {
			// just start again:
			mp.start();
		} else if (loop > 0){
			this.currentLoop--;
			mp.start();
		} else {
			fireEvent(PlayerListener.END_OF_MEDIA, this);
		}
	}

}
