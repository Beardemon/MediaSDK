package com.android.media;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.baselib.utils.Utility;
import com.android.baselib.utils.ScreenUtils;
import com.android.baselib.utils.LogUtils;
import com.android.player.CommonPlayer;
import com.android.player.IPlayer;
import com.android.player.PlayerAttributes;
import com.android.player.PlayerType;

import java.io.IOException;

public class PlayerActivity extends AppCompatActivity implements View.OnClickListener {

    private TextureView mVideoView;
    private ImageButton mControlBtn;
    private TextView mTimeView;
    private SeekBar mProgressView;
    private TextView mPlayTipView;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private float mPixelRatio; //SAR
    private float mDarRatio;
    private long mDuration = 0L;

    private CommonPlayer mPlayer;
    private Surface mSurface;
    private String mUrl = "https://tv.youkutv.cc/2020/01/15/SZpLQDUmJZKF9O0D/playlist.m3u8";
    private int mPlayerType = -1;
    private boolean mVideoCached = false;
    private int mPercent = 0;
    private long mCacheSize = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mUrl = getIntent().getStringExtra("url");
        mPlayerType = getIntent().getIntExtra("playerType", -1);
        if (mPlayerType == -1) {
            mPlayerType = 1;
        }
        mVideoCached = getIntent().getBooleanExtra("videoCached", false);
        mSurfaceWidth = ScreenUtils.getScreenWidth(this);
        initViews();
    }

    private void initViews() {
        mVideoView = (TextureView) findViewById(R.id.video_view);
        mTimeView = (TextView) findViewById(R.id.video_time_view);
        mProgressView = (SeekBar) findViewById(R.id.video_progress_view);
        mControlBtn = (ImageButton) findViewById(R.id.video_control_btn);
        mPlayTipView = (TextView) findViewById(R.id.play_tip_view);

        mControlBtn.setOnClickListener(this);
        mVideoView.setSurfaceTextureListener(mSurfaceTextureListener);
        mProgressView.setOnSeekBarChangeListener(mSeekBarChangeListener);
    }

    private void initPlayer() {

        PlayerAttributes attributes = new PlayerAttributes("");
        attributes.setVideoCacheSwitch(mVideoCached);

        if (mPlayerType == 1) {
            mPlayer = new CommonPlayer(this, PlayerType.IJK_PLAYER, attributes);
        } else if (mPlayerType == 2) {
            mPlayer = new CommonPlayer(this, PlayerType.EXO_PLAYER, attributes);
        } else if (mPlayerType == 3) {
            mPlayer = new CommonPlayer(this, PlayerType.MEDIA_PLAYER, attributes);
        }

        if (mVideoCached) {
            mPlayer.setOnLocalProxyCacheListener(mOnLocalProxyCacheListener);
            mPlayer.startLocalProxy(mUrl);
        } else {
            Uri uri = Uri.parse(mUrl);
            try {
                mPlayer.setDataSource(PlayerActivity.this, uri);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            mPlayer.setSurface(mSurface);
            mPlayer.setOnPreparedListener(mPreparedListener);
            mPlayer.setOnErrorListener(mErrorListener);
            mPlayer.setOnVideoSizeChangedListener(mVideoSizeChangeListener);
            mPlayer.prepareAsync();
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurface = new Surface(surface);
            initPlayer();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mPlayer != null) {
                mHandler.removeMessages(MSG_UPDATE_PROGRESS);
            }
            LogUtils.d("onStartTrackingTouch progress="+mProgressView.getProgress());
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            LogUtils.d("onStopTrackingTouch progress="+mProgressView.getProgress());

            if (mPlayer != null) {
                int progress = mProgressView.getProgress();
                int seekPosition = (int)(progress * 1.0f / MAX_PROGRESS * mDuration);
                mPlayer.seekTo(seekPosition);

                mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (mPlayer != null) {
            mPlayer.pause();
            mControlBtn.setBackgroundResource(R.mipmap.paused_state);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeMessages(MSG_UPDATE_PROGRESS);
        doReleasePlayer();
    }

    private IPlayer.OnPreparedListener mPreparedListener = new IPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(IPlayer mp) {
            doPlayVideo();
        }
    };

    private IPlayer.OnErrorListener mErrorListener = new IPlayer.OnErrorListener() {
        @Override
        public void onError(IPlayer mp, int what, String msg) {
            Toast.makeText(PlayerActivity.this, "Play Error", Toast.LENGTH_SHORT).show();
        }
    };

    private IPlayer.OnVideoSizeChangedListener mVideoSizeChangeListener = new IPlayer.OnVideoSizeChangedListener() {

        @Override
        public void onVideoSizeChanged(IPlayer mp, int width, int height, int rotationDegree, float pixelRatio, float darRatio) {

            LogUtils.d("PlayerActivity onVideoSizeChanged width="+width+", height="+height + ", mDarRatio = " + darRatio);
            mVideoWidth = width;
            mVideoHeight = height;
            mPixelRatio = pixelRatio;
            mDarRatio = darRatio;
            if (mPlayerType != 1 || Math.abs(mDarRatio) < 0.001f) {
                mSurfaceHeight = (int) (mSurfaceWidth * mVideoHeight * 1.0f / mVideoWidth);
            } else {
                mSurfaceHeight = (int) (mSurfaceWidth * 1.0f / mDarRatio);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(mSurfaceWidth, mSurfaceHeight);
            params.gravity = Gravity.CENTER;
            mVideoView.setLayoutParams(params);
        }
    };

    private IPlayer.OnLocalProxyCacheListener mOnLocalProxyCacheListener = new IPlayer.OnLocalProxyCacheListener() {
        @Override
        public void onCacheReady(IPlayer mp, String proxyUrl) {
            Uri uri = Uri.parse(proxyUrl);
            try {
                mPlayer.setDataSource(PlayerActivity.this, uri);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            mPlayer.setSurface(mSurface);
            mPlayer.setOnPreparedListener(mPreparedListener);
            mPlayer.setOnVideoSizeChangedListener(mVideoSizeChangeListener);
            mPlayer.setOnErrorListener(mErrorListener);
            mPlayer.prepareAsync();
            mPlayTipView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onCacheProgressChanged(IPlayer mp, int percent, long cachedSize) {
            mPercent = percent;
            mCacheSize = cachedSize;
            mPlayTipView.setText("边下边播： " + Utility.getSize(cachedSize));
        }

        @Override
        public void onCacheSpeedChanged(IPlayer mp, float speed) {
        }

        @Override
        public void onCacheForbidden(IPlayer mp, String url) {
            LogUtils.w("onCacheForbidden url = " + url);
        }

        @Override
        public void onCacheFinished(IPlayer mp) {
            mPercent = 100;
        }
    };

    private static final int MSG_UPDATE_PROGRESS = 1;
    private static final int MAX_PROGRESS = 1000;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_PROGRESS) {
                updateProgressView();
            }
        }
    };

    @Override
    public void onClick(View view) {
        LogUtils.e("click event");
        if(view == mControlBtn) {
            if (!mPlayer.isPlaying()) {
                mPlayer.start();
                mControlBtn.setBackgroundResource(R.mipmap.played_state);
            } else {
                mPlayer.pause();
                mControlBtn.setBackgroundResource(R.mipmap.paused_state);
            }
        }
    }

    private void doPlayVideo() {
        if (mPlayer != null) {
            mTimeView.setVisibility(View.VISIBLE);
            mPlayer.start();
            mControlBtn.setBackgroundResource(R.mipmap.played_state);
            mDuration = mPlayer.getDuration();
            LogUtils.d("total duration ="+mDuration +", timeString="+ Utility.getVideoTimeString(mDuration));
            mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
        }
    }

    private void updateProgressView() {
        if (mPlayer != null) {
            long currentPosition = mPlayer.getCurrentPosition();
            mTimeView.setText(Utility.getVideoTimeString(currentPosition) + " / " + Utility.getVideoTimeString(mDuration));
            mProgressView.setProgress((int)(1000 *  currentPosition * 1.0f / mDuration));
            int cacheProgress = (int)(mPercent * 1.0f / 100 * 1000);
            mProgressView.setSecondaryProgress(cacheProgress);
        }
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, 1000);
    }

    private void doReleasePlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}
