/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.util;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.Vibrator;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;

import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.shell.AppClassLoader;
import javax.microedition.shell.MicroActivity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import ru.playsoftware.j2meloader.config.Config;

public class ContextHolder {

	private static Display display;
	private static VirtualKeyboard vk;
	private static WeakReference<MicroActivity> currentActivity;		// 在这里实例化了，空的
	private static Vibrator vibrator;
	private static Context appContext;
	private static final ArrayList<ActivityResultListener> resultListeners = new ArrayList<>();
	private static boolean vibrationEnabled;

	public static Context getAppContext() {		// 这个函数用于返回application
		System.out.println("##########由此进入2#################");
		return appContext;
	}

	public static VirtualKeyboard getVk() {
//		return vk;
		return null;
	}

	public static void setVk(VirtualKeyboard vk) {
		ContextHolder.vk = vk;
	}

	private static Display getDisplay() {
//		System.out.println("##########由此进入3#################");
		if (display == null) {
			// 从getAppContext中得到activity
			display = ((WindowManager) Objects.requireNonNull(getAppContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
		}
		return display;
	}

	public static int getDisplayWidth() {		// 得到宽高
//		System.out.println("##########由此进入4#################");
		return getDisplay().getWidth();
	}

	public static int getDisplayHeight() {		// 得到宽高
//		System.out.println("##########由此进入5#################");
		return getDisplay().getHeight();
	}

	public static void setCurrentActivity(MicroActivity activity) {		// 应该由此进入
		System.out.println("##########由此进入4#################");
		currentActivity = new WeakReference<>(activity);
	}

	public static void addActivityResultListener(ActivityResultListener listener) {
		if (!resultListeners.contains(listener)) {
			resultListeners.add(listener);
		}
	}

	public static void removeActivityResultListener(ActivityResultListener listener) {
		resultListeners.remove(listener);
	}

	public static void notifyOnActivityResult(int requestCode, int resultCode, Intent data) {
		for (ActivityResultListener listener : resultListeners) {
			listener.onActivityResult(requestCode, resultCode, data);
		}
	}

	public static InputStream getResourceAsStream(Class resClass, String resName) {
		return AppClassLoader.getResourceAsStream(resClass, resName);
	}

	public static FileOutputStream openFileOutput(String name) throws FileNotFoundException {
		File dir = new File(AppClassLoader.getDataDir());
		File file = new File(dir, name);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new FileNotFoundException("Can't create directory: " + dir);
		}
		return new FileOutputStream(file);
	}

	public static FileInputStream openFileInput(String name) throws FileNotFoundException {
		return new FileInputStream(getFileByName(name));
	}

	public static boolean deleteFile(String name) {
		return getFileByName(name).delete();
	}

	public static File getFileByName(String name) {
		return new File(AppClassLoader.getDataDir(), name);
	}

	public static File getCacheDir() {		// 应该是为了得到文件夹
//		System.out.println("##########由此进入4#################");
		return getAppContext().getExternalCacheDir();
	}

	public static boolean requestPermission(String permission) {
		if (ContextCompat.checkSelfPermission(currentActivity.get(), permission) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(currentActivity.get(), new String[]{permission}, 0);
			return false;
		} else {
			return true;
		}
	}

	public static String getAssetAsString(String fileName) {	// 没用
//		System.out.println("##########由此进入5#################");
		StringBuilder sb = new StringBuilder();

		try (InputStream is = getAppContext().getAssets().open(fileName);
			 BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
			String str;
			while ((str = br.readLine()) != null) {
				sb.append(str).append('\n');
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return sb.toString();
	}

	public static MicroActivity getActivity() {
//		System.out.println("##########由此进入8#################");
		return currentActivity.get();
	}

	public static boolean vibrate(int duration) {		// 没用
//		System.out.println("##########由此进入6#################");
		if (!vibrationEnabled) {
			return false;
		}
		if (vibrator == null) {
			vibrator = (Vibrator) getAppContext().getSystemService(Context.VIBRATOR_SERVICE);
		}
		if (vibrator == null || !vibrator.hasVibrator()) {
			return false;
		}
		if (duration > 0) {
			vibrator.vibrate(duration);
		} else if (duration < 0) {
			throw new IllegalStateException();
		} else {
			vibrator.cancel();
		}
		return true;
	}

	public static void vibrateKey(int duration) {		// 没用
//		System.out.println("##########由此进入7#################");
		if (vibrator == null) {
			vibrator = (Vibrator) getAppContext().getSystemService(Context.VIBRATOR_SERVICE);
		}
		if (vibrator == null || !vibrator.hasVibrator()) {
			return;
		}
		vibrator.vibrate(duration);
	}

	public static void setApplication(Application application) {
		appContext = application;
	}

	public static void setVibration(boolean vibrationEnabled) {
		ContextHolder.vibrationEnabled = vibrationEnabled;
	}
}
