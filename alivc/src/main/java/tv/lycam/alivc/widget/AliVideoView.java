package tv.lycam.alivc.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.MediaPlayer;
import com.alivc.player.ScalableType;
import com.alivc.player.ScaleManager;
import com.alivc.player.Size;

import javax.microedition.khronos.opengles.GL10;

import tv.lycam.alivc.IMediaPlayer;
import tv.lycam.alivc.PlayerState;
import tv.lycam.alivc.utils.Debugger;

/**
 * @author 诸葛不亮
 * @version 1.0
 * @description
 */

public class AliVideoView extends IVideoView implements TextureView.SurfaceTextureListener {

    public static final String TAG = "AliVideoView";
    // 默认缩放模式
    protected MediaPlayer.VideoScalingMode DEFAULT_ASPECTRATIO = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT;


    private AliVcMediaPlayer mMediaPlayer;

    private ScaleManager mScaleManager;
    // 变换
    private ScalableType mScalableType = ScalableType.NONE;

    private IMediaPlayer mIMediaPlayer;

    // 当前播放状态
    protected int mCurrentState = PlayerState.CURRENT_STATE_NORMAL;
    // 备份缓存前的播放状态
    protected int mBackUpPlayingBufferState = -1;
    // 是否直播流
    protected boolean isLiveStream;
    protected String mStreamUrl;
    // 判断是否播放过
    private boolean hadPlay;

    public AliVideoView(Context context) {
        this(context, null);
    }

    public AliVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AliVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVodPlayer(context);
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        if (mMediaPlayer != null) {
            Surface surface = new Surface(surfaceTexture);
            mMediaPlayer.setVideoSurface(surface);
            surface.release();
        }
        int vWidth = getWidth();
        int vHeight = getHeight();
        Size viewSize = new Size(vWidth, vHeight);
        Size videoSize = new Size(width, height);
        mScaleManager = new ScaleManager(viewSize, videoSize);
        resolveTransform();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurfaceChanged();
        }
        resolveTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        resolveTransform();
    }

    private int createTextureID() {
        int[] texture = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }

    private void initVodPlayer(Context context) {
        // 经测试，此处的textureid可随意数字，原因尚不知。
        SurfaceTexture surfaceTexture = new SurfaceTexture(createTextureID());
        Surface surface = new Surface(surfaceTexture);
        mMediaPlayer = new AliVcMediaPlayer(context, surface);
        surface.release();
        surfaceTexture.release();

        //音频数据回调接口，在需要处理音频时使用，如拿到视频音频，然后绘制音柱。
        mMediaPlayer.setPcmDataListener(new MediaPlayer.MediaPlayerPcmDataListener() {
            @Override
            public void onPcmData(byte[] bytes, int i) {

            }
        });
        mMediaPlayer.setPreparedListener(mOnBasePreparedListener);
        mMediaPlayer.setInfoListener(mOnBaseInfoListener);
        mMediaPlayer.setVideoSizeChangeListener(mOnBaseVideoSizeChangeListener);
        mMediaPlayer.setBufferingUpdateListener(mOnBaseBufferingUpdateListener);
        mMediaPlayer.setCompletedListener(mOnBaseCompletedListener);
        mMediaPlayer.setErrorListener(mOnBaseErrorListener);
        mMediaPlayer.setSeekCompleteListener(mOnBaseSeekCompleteListener);
        mMediaPlayer.setFrameInfoListener(mOnBaseFrameInfoListener);
        mMediaPlayer.setStoppedListener(mOnBaseStoppedListener);
        mMediaPlayer.setVideoScalingMode(DEFAULT_ASPECTRATIO);
        mMediaPlayer.disableNativeLog();
    }

    //准备完成时触发
    private AliVcMediaPlayer.MediaPlayerPreparedListener mOnBasePreparedListener = new AliVcMediaPlayer.MediaPlayerPreparedListener() {
        @Override
        public void onPrepared() {
//            if (mCurrentState != PlayerState.CURRENT_STATE_PREPAREING) return;
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaPrepared();
            }
            mMediaPlayer.play();
            setStateAndUi(PlayerState.CURRENT_STATE_PLAYING);
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared();
            }
        }
    };

    private AliVcMediaPlayer.MediaPlayerInfoListener mOnBaseInfoListener = new AliVcMediaPlayer.MediaPlayerInfoListener() {
        @Override
        public void onInfo(int what, int extra) {
            Debugger.printfLog(TAG, "OnInfo, what = " + what + ", extra = " + extra);
            switch (what) {
                case AliVcMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    mBackUpPlayingBufferState = mCurrentState;
                    //避免在onPrepared之前就进入了buffering，导致一只loading
                    if (mCurrentState != PlayerState.CURRENT_STATE_PREPAREING && mCurrentState > 0)
                        setStateAndUi(PlayerState.CURRENT_STATE_PLAYING_BUFFERING_START);
                    break;
                case AliVcMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    if (mBackUpPlayingBufferState != -1) {
                        if (mCurrentState != PlayerState.CURRENT_STATE_PREPAREING && mCurrentState > 0)
                            setStateAndUi(mBackUpPlayingBufferState);

                        mBackUpPlayingBufferState = -1;
                    }
                    break;
                case AliVcMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Debugger.printfLog(TAG, "First video render time: " + extra + "ms");
                    break;
                default:
                    break;
            }
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaInfo(what, extra);
            }
            if (mOnInfoListener != null) {
                mOnInfoListener.onInfo(what, extra);
            }
        }

    };

    //错误发生时触发，错误码见接口文档
    private AliVcMediaPlayer.MediaPlayerErrorListener mOnBaseErrorListener = new AliVcMediaPlayer.MediaPlayerErrorListener() {
        @Override
        public void onError(int errorCode, String msg) {
            setStateAndUi(PlayerState.CURRENT_STATE_ERROR);
            mMediaPlayer.stop();
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaError(errorCode, msg);
            }
            if (mOnErrorListener != null) {
                mOnErrorListener.onError(errorCode, msg);
            }
        }
    };

    //视频正常播放完成时触发
    private AliVcMediaPlayer.MediaPlayerCompletedListener mOnBaseCompletedListener = new AliVcMediaPlayer.MediaPlayerCompletedListener() {
        @Override
        public void onCompleted() {
            setStateAndUi(PlayerState.CURRENT_STATE_AUTO_COMPLETE);
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaCompleted();
            }
            if (mOnCompletedListener != null) {
                mOnCompletedListener.onCompleted();
            }
        }
    };

    private AliVcMediaPlayer.MediaPlayerBufferingUpdateListener mOnBaseBufferingUpdateListener = new AliVcMediaPlayer.MediaPlayerBufferingUpdateListener() {
        @Override
        public void onBufferingUpdateListener(int percent) {
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaBufferingUpdate(percent);
            }
            if (mOnBufferingUpdateListener != null) {
                mOnBufferingUpdateListener.onBufferingUpdateListener(percent);
            }
        }
    };

    private AliVcMediaPlayer.MediaPlayerVideoSizeChangeListener mOnBaseVideoSizeChangeListener = new AliVcMediaPlayer.MediaPlayerVideoSizeChangeListener() {
        @Override
        public void onVideoSizeChange(int width, int height) {
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaVideoSizeChange(width, height);
            }
            if (mOnVideoSizeChangeListener != null) {
                mOnVideoSizeChangeListener.onVideoSizeChange(width, height);
            }
        }
    };

    private AliVcMediaPlayer.MediaPlayerSeekCompleteListener mOnBaseSeekCompleteListener = new AliVcMediaPlayer.MediaPlayerSeekCompleteListener() {
        @Override
        public void onSeekCompleted() {
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaSeekCompleted();
            }
        }
    };

    //首帧显示时触发
    private MediaPlayer.MediaPlayerFrameInfoListener mOnBaseFrameInfoListener = new MediaPlayer.MediaPlayerFrameInfoListener() {
        @Override
        public void onFrameInfoListener() {
            if (mIMediaPlayer != null) {
                mIMediaPlayer.onMediaFrameInfo();
            }
            if (mOnFrameInfoListener != null) {
                mOnFrameInfoListener.onFrameInfoListener();
            }
        }
    };

    private MediaPlayer.MediaPlayerStoppedListener mOnBaseStoppedListener = new MediaPlayer.MediaPlayerStoppedListener() {
        @Override
        public void onStopped() {
            setStateAndUi(PlayerState.CURRENT_STATE_NORMAL);
        }
    };


    private AliVcMediaPlayer.MediaPlayerPreparedListener mOnPreparedListener;
    private AliVcMediaPlayer.MediaPlayerInfoListener mOnInfoListener;
    private AliVcMediaPlayer.MediaPlayerErrorListener mOnErrorListener;
    private AliVcMediaPlayer.MediaPlayerCompletedListener mOnCompletedListener;
    private AliVcMediaPlayer.MediaPlayerBufferingUpdateListener mOnBufferingUpdateListener;
    private AliVcMediaPlayer.MediaPlayerVideoSizeChangeListener mOnVideoSizeChangeListener;
    private AliVcMediaPlayer.MediaPlayerFrameInfoListener mOnFrameInfoListener;


    public void setOnPreparedListener(AliVcMediaPlayer.MediaPlayerPreparedListener onPreparedListener) {
        mOnPreparedListener = onPreparedListener;
    }

    public void setOnInfoListener(AliVcMediaPlayer.MediaPlayerInfoListener onInfoListener) {
        mOnInfoListener = onInfoListener;
    }

    public void setOnErrorListener(AliVcMediaPlayer.MediaPlayerErrorListener onErrorListener) {
        mOnErrorListener = onErrorListener;
    }

    public void setOnCompletedListener(AliVcMediaPlayer.MediaPlayerCompletedListener onCompletedListener) {
        mOnCompletedListener = onCompletedListener;
    }

    public void setOnBufferingUpdateListener(AliVcMediaPlayer.MediaPlayerBufferingUpdateListener onBufferingUpdateListener) {
        mOnBufferingUpdateListener = onBufferingUpdateListener;
    }

    public void setOnVideoSizeChangeListener(AliVcMediaPlayer.MediaPlayerVideoSizeChangeListener onVideoSizeChangeListener) {
        mOnVideoSizeChangeListener = onVideoSizeChangeListener;
    }

    public void setOnFrameInfoListener(AliVcMediaPlayer.MediaPlayerFrameInfoListener onFrameInfoListener) {
        mOnFrameInfoListener = onFrameInfoListener;
    }

    private void setStateAndUi(int state) {
        if (state != PlayerState.CURRENT_STATE_NORMAL) {
            hadPlay = true;
        }
        mCurrentState = state;
        if (mIMediaPlayer != null) {
            mIMediaPlayer.setStateAndUi(state);
        }
    }

    /**
     * 处理镜像旋转
     * 注意，暂停时
     */
    protected void resolveTransform() {
        if (mScaleManager != null) {
            Matrix scaleMatrix = mScaleManager.getScaleMatrix(mScalableType);
            setTransform(scaleMatrix);
            invalidate();
        }
    }


    public void setScalableType(ScalableType scalableType) {
        this.mScalableType = scalableType;
        resolveTransform();
    }

    public void setIMediaPlayer(IMediaPlayer IMediaPlayer) {
        mIMediaPlayer = IMediaPlayer;
    }

    public void setVideoPath(String url) {
        boolean isLiveStream = isLive(url);
        setVideoPath(url, isLiveStream);
    }

    public void setVideoPath(String url, boolean isLiveStream) {
        this.mStreamUrl = url;
        this.isLiveStream = isLiveStream;
    }

    /**
     * @param decoderType 解码器类型。0代表硬件解码器；1代表软件解码器。
     */
    public void setDefaultDecoder(int decoderType) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setDefaultDecoder(decoderType);
        }
    }

    public void pause() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            setStateAndUi(PlayerState.CURRENT_STATE_PAUSE);
        }
    }

    public void resume() {
        if (mCurrentState == PlayerState.CURRENT_STATE_PAUSE) {
            if (mMediaPlayer != null) {
                mMediaPlayer.play();
                setStateAndUi(PlayerState.CURRENT_STATE_PLAYING);
            }
        }
    }

    protected void prepareAndPlay() {
        if (TextUtils.isEmpty(mStreamUrl)) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setMediaType(isLiveStream ? MediaPlayer.MediaType.Live : MediaPlayer.MediaType.Vod);
            mMediaPlayer.prepareAndPlay(mStreamUrl);
            setStateAndUi(PlayerState.CURRENT_STATE_PREPAREING);
        }
    }

    protected void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            setStateAndUi(PlayerState.CURRENT_STATE_NORMAL);
        }
    }

    protected void destroy() {
        if (mMediaPlayer != null) {
            mMediaPlayer.releaseVideoSurface();
            mMediaPlayer.stop();
            mMediaPlayer.destroy();
        }
    }

    public void start() {
        if (mMediaPlayer != null && hadPlay) {
            mMediaPlayer.stop();
        }
        prepareAndPlay();
    }
}
