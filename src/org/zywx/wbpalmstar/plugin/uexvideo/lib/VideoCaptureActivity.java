/**
 * Copyright 2014 Jeroen Mols
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zywx.wbpalmstar.plugin.uexvideo.lib;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.provider.MediaStore.Video.Thumbnails;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.zywx.wbpalmstar.base.BConstant;
import org.zywx.wbpalmstar.base.ResoureFinder;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.camera.CameraWrapper;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.camera.NativeCamera;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.configuration.CaptureConfiguration;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.preview.SensorController;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.recorder.AlreadyUsedException;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.recorder.VideoRecorder;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.recorder.VideoRecorderInterface;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.view.RecordingButtonInterface;
import org.zywx.wbpalmstar.plugin.uexvideo.lib.view.VideoCaptureView;

public class VideoCaptureActivity extends Activity implements RecordingButtonInterface, VideoRecorderInterface {

    public static final int RESULT_ERROR = 753245;

    public static final String EXTRA_OUTPUT_FILENAME       = "com.jmolsmobile.extraoutputfilename";
    public static final String EXTRA_CAPTURE_CONFIGURATION = "com.jmolsmobile.extracaptureconfiguration";
    public static final String EXTRA_ERROR_MESSAGE         = "com.jmolsmobile.extraerrormessage";

    private static final   String SAVED_RECORDED_BOOLEAN = "com.jmolsmobile.savedrecordedboolean";
    protected static final String SAVED_OUTPUT_FILENAME  = "com.jmolsmobile.savedoutputfilename";

    private boolean mVideoRecorded = false;
    VideoFile mVideoFile = null;
    private CaptureConfiguration mCaptureConfiguration;

    private VideoCaptureView mVideoCaptureView;
    private VideoRecorder    mVideoRecorder;
    private ResoureFinder    finder;
    private boolean mFlashOpen = false;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CLog.toggleLogging(this);
        finder = ResoureFinder.getInstance(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(finder.getLayoutId("plugin_video_activity_videocapture"));

        initializeCaptureConfiguration(savedInstanceState);

        mVideoCaptureView = (VideoCaptureView) findViewById(finder.getId("videocapture_videocaptureview_vcv"));
        if (mVideoCaptureView == null) return; // Wrong orientation

        initializeRecordingUI();
    }

    private void initializeCaptureConfiguration(final Bundle savedInstanceState) {
        mCaptureConfiguration = generateCaptureConfiguration();
        mVideoRecorded = generateVideoRecorded(savedInstanceState);
        mVideoFile = generateOutputFile(savedInstanceState);
    }

    private void initializeRecordingUI() {
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        mVideoRecorder = new VideoRecorder(this, mCaptureConfiguration, mVideoFile, new CameraWrapper(new NativeCamera(), display.getRotation()),
                mVideoCaptureView.getPreviewSurfaceHolder());
        mVideoCaptureView.setRecordingButtonInterface(this);

        if (mVideoRecorded) {
            mVideoCaptureView.updateUIRecordingFinished(getVideoThumbnail());
        } else {
            mVideoCaptureView.updateUINotRecording();
        }
    }

    @Override
    protected void onPause() {
        if (mVideoRecorder != null) {
            mVideoRecorder.stopRecording(null);
        }
        releaseAllResources();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        finishCancelled();
    }

    @Override
    public void onRecordButtonClicked() {
        try {
            mVideoRecorder.toggleRecording();
        } catch (AlreadyUsedException e) {
            CLog.d(CLog.ACTIVITY, "Cannot toggle recording after cleaning up all resources");
        }
    }

    @Override
    public void onAcceptButtonClicked() {
        finishCompleted();
    }

    @Override
    public void onDeclineButtonClicked() {
        finishCancelled();
    }

    @Override
    public void onChangeCameraButtonClicked() {
        boolean isBack=mVideoRecorder.getCameraWrapper().getNativeCamera().isBackCamera();
        mVideoCaptureView.setFlashButtonBg(!isBack);
        mVideoCaptureView.setFlashBtnVisible(!isBack);
        mVideoRecorder.changeCamera();

    }

    @Override
    public void onFlashButtonClicked() {
         if (mFlashOpen) {
            mVideoRecorder.closeFlash();
            mFlashOpen=false;
        }else {
            mVideoRecorder.openFlash();
            mFlashOpen=true;
        }
        mVideoCaptureView.setFlashButtonBg(mFlashOpen);
    }

    @Override
    public void onRecordingStarted() {
        mVideoCaptureView.updateUIRecordingOngoing();
        mVideoCaptureView.startTimer();
        mVideoCaptureView.recordMode(true);
    }

    @Override
    public void onRecordingStopped(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        mVideoCaptureView.updateUIRecordingFinished(getVideoThumbnail());
        releaseAllResources();
        mVideoCaptureView.stopTimer();
    }

    @Override
    public void onRecordingSuccess() {
        mVideoRecorded = true;
    }

    @Override
    public void onRecordingFailed(String message) {
        finishError(message);
    }

    private void finishCompleted() {
        final Intent result = new Intent();
        result.putExtra(EXTRA_OUTPUT_FILENAME, mVideoFile.getFullPath());
        this.setResult(RESULT_OK, result);
        finish();
    }

    private void finishCancelled() {
        this.setResult(RESULT_CANCELED);
        finish();
    }

    private void finishError(final String message) {
        Toast.makeText(getApplicationContext(), "Can't capture video: " + message, Toast.LENGTH_LONG).show();

        final Intent result = new Intent();
        result.putExtra(EXTRA_ERROR_MESSAGE, message);
        this.setResult(RESULT_ERROR, result);
        finish();
    }

    private void releaseAllResources() {
        if (mVideoRecorder != null) {
            mVideoRecorder.releaseAllResources();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(SAVED_RECORDED_BOOLEAN, mVideoRecorded);
        savedInstanceState.putString(SAVED_OUTPUT_FILENAME, mVideoFile.getFullPath());
        super.onSaveInstanceState(savedInstanceState);
    }

    protected CaptureConfiguration generateCaptureConfiguration() {
        CaptureConfiguration returnConfiguration = this.getIntent().getParcelableExtra(EXTRA_CAPTURE_CONFIGURATION);
        if (returnConfiguration == null) {
            returnConfiguration = new CaptureConfiguration();
            CLog.d(CLog.ACTIVITY, "No captureconfiguration passed - using default configuration");
        }
        return returnConfiguration;
    }

    private boolean generateVideoRecorded(final Bundle savedInstanceState) {
        if (savedInstanceState == null) return false;
        return savedInstanceState.getBoolean(SAVED_RECORDED_BOOLEAN, false);
    }

    protected VideoFile generateOutputFile(Bundle savedInstanceState) {
        VideoFile returnFile;
        if (savedInstanceState != null) {
            returnFile = new VideoFile(savedInstanceState.getString(SAVED_OUTPUT_FILENAME));
        } else {
            returnFile = new VideoFile(this.getIntent().getStringExtra(EXTRA_OUTPUT_FILENAME));
        }
        // TODO: add checks to see if outputfile is writeable
        return returnFile;
    }

    public Bitmap getVideoThumbnail() {
        final Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(mVideoFile.getFullPath(),
                Thumbnails.FULL_SCREEN_KIND);
        if (thumbnail == null) {
            CLog.d(CLog.ACTIVITY, "Failed to generate video preview");
        }
        return thumbnail;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i("TAG", "is protrait:" + ( newConfig.orientation == Configuration.ORIENTATION_PORTRAIT));
    }


    @Override
    protected void onStart() {
        super.onStart();
        SensorController.getInstance(BConstant.app).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SensorController.getInstance(BConstant.app).stop();
    }
}
