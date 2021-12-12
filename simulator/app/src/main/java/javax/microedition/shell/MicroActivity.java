/*
 * Copyright 2015-2016 Nickolay Savchenko
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

package javax.microedition.shell;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Objects;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.CanvasEvent;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.KeyMapper;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.lcdui.overlay.OverlayView;
import javax.microedition.util.ContextHolder;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.EmulatorApplication;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;

import static ru.playsoftware.j2meloader.util.Constants.*;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;

	private Displayable current;
	private boolean visible;
	private boolean actionBarEnabled;
	private boolean statusBarEnabled;
	private FrameLayout layout;
	private Toolbar toolbar;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private int menuKey;

	BluetoothSocket socket = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme();
		super.onCreate(savedInstanceState);
		System.out.println("##########由此进入3#################");
		ContextHolder.setCurrentActivity(this);
		setContentView(R.layout.activity_micro);		// 这是游戏界面
		OverlayView overlayView = findViewById(R.id.vOverlay);
		layout = findViewById(R.id.displayable_container);
		toolbar = findViewById(R.id.toolbar);		// 这是顶层工具箱
		setSupportActionBar(toolbar);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		actionBarEnabled = sp.getBoolean(PREF_TOOLBAR, false);
		statusBarEnabled = sp.getBoolean(PREF_STATUSBAR, false);
		if (sp.getBoolean(PREF_KEEP_SCREEN, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		ContextHolder.setVibration(sp.getBoolean(PREF_VIBRATION, true));
		Intent intent = getIntent();
		appName = intent.getStringExtra(KEY_MIDLET_NAME);
		Uri data = intent.getData();
		if (data == null) {
			showErrorDialog("Invalid intent: app path is null");
			return;
		}
		String appPath = data.toString();	// 应该是关于数据加载的
		microLoader = new MicroLoader(this, appPath);
		if (!microLoader.init()) {
			Config.startApp(this, appName, appPath, true);
			finish();
			return;
		}
		microLoader.applyConfiguration();		// 不能删

		// 以下不能删
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		try {
			loadMIDlet();
		} catch (Exception e) {
			e.printStackTrace();
			showErrorDialog(e.toString());
		}

/*********************在此出填写通信协议***************************************************/
		Button button1 = findViewById(R.id.iii);
		button1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Canvas canvas = (Canvas) current;
				Display.postEvent(CanvasEvent.getInstance(canvas, CanvasEvent.KEY_PRESSED, KeyMapper.convertKeyCode(-7)));
			}
		});
		Button button2 = findViewById(R.id.jjj);
		button2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Canvas canvas = (Canvas) current;
				Display.postEvent(CanvasEvent.getInstance(canvas, CanvasEvent.KEY_PRESSED, KeyMapper.convertKeyCode(-5)));
			}
		});
/*********************在此出填写通信协议***************************************************/

		System.out.println("##########由此进入7#################");
		socket = ((EmulatorApplication)getApplication()).getSocket();		// 获取已连接的socket
		ReceiveDataThread res_data = new ReceiveDataThread();				// 运行接收数据线程
		res_data.start();
	}

	public class ReceiveDataThread extends Thread{
		private InputStream inputStream;
		public ReceiveDataThread() {
			super();
			try {
				//获取连接socket的输入流
				inputStream = socket.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		@Override
		public void run() {
			super.run();
			byte[] buffer = new byte[1];    // 协议字节多长，就定多长，一个十六进制数是1个字节
			while (true){
				try {
					inputStream.read(buffer);		// 获取接收的数据
/**********************在此填写通信协议****************************************/
					int data = (buffer[0]);
					control(data);
/**********************在此填写通信协议****************************************/
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		public void press_key(int KEY_CODE){		// 模拟按键按下
			Canvas canvas = (Canvas) current;		// 必须不断更新状态
			Display.postEvent(CanvasEvent.getInstance(canvas, CanvasEvent.KEY_PRESSED, KeyMapper.convertKeyCode(KEY_CODE)));	// 发送键值
		}
		public void release_key(int KEY_CODE){		// 模拟按键松开
			Canvas canvas = (Canvas) current;		// 必须不断更新状态
			Display.postEvent(CanvasEvent.getInstance(canvas, CanvasEvent.KEY_RELEASED, KeyMapper.convertKeyCode(KEY_CODE)));	// 发送键值
		}
		public void control(int data){
			switch (data){
				case 0x01:	press_key(-1);	break;				// 上 按下
				case 0x02:	press_key(-2);	break;				// 下 按下
				case 0x03:	press_key(-3);	break;				// 左 按下
				case 0x04:	press_key(-4);	break;				// 右 按下
				case 0x05:	press_key(-5);	break;				// 中 按下
				case 0x06:	press_key(-6);	break;				// 选项 按下
				case 0x07:	press_key(-7);	break;				// 返回 按下
				case 0x08:	press_key(49);	break;				// 1 按下
				case 0x09:	press_key(50);	break;				// 2 按下
				case 0x10:	press_key(51);	break;				// 3 按下
				case 0x11:	press_key(52);	break;				// 4 按下
				case 0x12:	press_key(53);	break;				// 5 按下
				case 0x13:	press_key(54);	break;				// 6 按下
				case 0x14:	press_key(55);	break;				// 7 按下
				case 0x15:	press_key(56);	break;				// 8 按下
				case 0x16:	press_key(57);	break;				// 9 按下
				case 0x17:	press_key(42);	break;				// * 按下
				case 0x18:	press_key(48);	break;				// 0 按下
				case 0x19:	press_key(35);	break;				// # 按下
				case 0x20:	release_key(-1);	break;				// 上 松开
				case 0x21:	release_key(-2);	break;				// 下 松开
				case 0x22:	release_key(-3);	break;				// 左 松开
				case 0x23:	release_key(-4);	break;				// 右 松开
				case 0x24:	release_key(-5);	break;				// 中 松开
				case 0x25:	release_key(-6);	break;				// 选项 松开
				case 0x26:	release_key(-7);	break;				// 返回 松开
				case 0x27:	release_key(49);	break;				// 1 松开
				case 0x28:	release_key(50);	break;				// 2 松开
				case 0x29:	release_key(51);	break;				// 3 松开
				case 0x30:	release_key(52);	break;				// 4 松开
				case 0x31:	release_key(53);	break;				// 5 松开
				case 0x32:	release_key(54);	break;				// 6 松开
				case 0x33:	release_key(55);	break;				// 7 松开
				case 0x34:	release_key(56);	break;				// 8 松开
				case 0x35:	release_key(57);	break;				// 9 松开
				case 0x36:	release_key(42);	break;				// * 松开
				case 0x37:	release_key(48);	break;				// 0 松开
				case 0x38:	release_key(35);	break;				// # 松开

			}
		}
	}

	public void setTheme() {
		int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (current == Configuration.UI_MODE_NIGHT_YES) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
		setTheme(R.style.AppTheme_NoActionBar);

	}

	@Override
	public void onResume() {		// MicroActivity create完就会调用
		super.onResume();
		visible = true;
		System.out.println("##########由此进入5#################");
		MidletThread.resumeApp();
	}

	@Override
	public void onPause() {
		visible = false;
		hideSoftInput();
		MidletThread.pauseApp();
		super.onPause();
	}

	private void hideSoftInput() {
		if (inputMethodManager != null) {
			IBinder windowToken = layout.getWindowToken();
			inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && current instanceof Canvas) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		switch (orientation) {
			case ORIENTATION_AUTO:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				break;
			case ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
				break;
			case ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				break;
			case ORIENTATION_DEFAULT:
			default:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				break;
		}
	}

	private void loadMIDlet() throws Exception {
		LinkedHashMap<String, String> midlets = microLoader.loadMIDletList();
		int size = midlets.size();
		String[] midletsNameArray = midlets.values().toArray(new String[0]);
		String[] midletsClassArray = midlets.keySet().toArray(new String[0]);
		if (size == 0) {
			throw new Exception("No MIDlets found");
		} else if (size == 1) {
			MidletThread.create(microLoader, midletsClassArray[0]);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] names, final String[] classes) {		// 这是显示日志的
//		System.out.println("##########由此进入4#################");
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(names, (d, n) -> {
					String clazz = classes[n];
					ErrorReporter errorReporter = ACRA.getErrorReporter();
					String report = errorReporter.getCustomData(Constants.KEY_APPCENTER_ATTACHMENT);
					StringBuilder sb = new StringBuilder();
					if (report != null) {
						sb.append(report).append("\n");
					}
					sb.append("Begin app: ").append(names[n]).append(", ").append(clazz);
					errorReporter.putCustomData(Constants.KEY_APPCENTER_ATTACHMENT, sb.toString());
					MidletThread.create(microLoader, clazz);

//					System.out.println("##########由此进入7#################");
					MidletThread.resumeApp();
				})
				.setOnCancelListener(d -> {
					d.dismiss();
					MidletThread.notifyDestroyed();
				});
		builder.show();
	}

	void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> MidletThread.notifyDestroyed());
		builder.setOnCancelListener(dialogInterface -> MidletThread.notifyDestroyed());
		builder.show();
	}

	private final SimpleEvent msgSetCurrent = new SimpleEvent() {
		@Override
		public void process() {
			current.clearDisplayableView();
			layout.removeAllViews();
			layout.addView(current.getDisplayableView());
			invalidateOptionsMenu();
			ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) toolbar.getLayoutParams();
			if (current instanceof Canvas) {
				hideSystemUI();
				if (!actionBarEnabled) {
					actionBar.hide();
				} else {
					final String title = current.getTitle();
					actionBar.setTitle(title == null ? appName : title);
					layoutParams.height = (int) (getToolBarHeight() / 1.5);
				}
			} else {
				showSystemUI();
				actionBar.show();
				final String title = current.getTitle();
				actionBar.setTitle(title == null ? appName : title);
				layoutParams.height = getToolBarHeight();
			}
			toolbar.setLayoutParams(layoutParams);
		}
	};

	private int getToolBarHeight() {
		int[] attrs = new int[]{R.attr.actionBarSize};
		TypedArray ta = obtainStyledAttributes(attrs);
		int toolBarHeight = ta.getDimensionPixelSize(0, -1);
		ta.recycle();
		return toolBarHeight;
	}

	private void hideSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			if (!statusBarEnabled) {
				flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
			}
			getWindow().getDecorView().setSystemUiVisibility(flags);
		} else if (!statusBarEnabled) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	private void showSystemUI() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
	}

	public void setCurrent(Displayable displayable) {
		current = displayable;
		ViewHandler.postEvent(msgSetCurrent);
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return visible;
	}

	public void showExitConfirmation() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setTitle(R.string.CONFIRMATION_REQUIRED)
				.setMessage(R.string.FORCE_CLOSE_CONFIRMATION)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					hideSoftInput();
					MidletThread.destroyApp();
				})
				.setNeutralButton(R.string.action_settings, (d, w) -> {
					hideSoftInput();
					Intent intent = getIntent();
					Config.startApp(this, intent.getStringExtra(KEY_MIDLET_NAME),
							intent.getDataString(), true);
					MidletThread.destroyApp();
				})
				.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.create().show();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU)
			if (current instanceof Canvas
					&& layout.dispatchKeyEvent(event)) {
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.getRepeatCount() == 0) {
					event.startTracking();
					return true;
				} else if (event.isLongPress()) {
					return onKeyLongPress(event.getKeyCode(), event);
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				return onKeyUp(event.getKeyCode(), event);
			}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void openOptionsMenu() {
		if (!actionBarEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && current instanceof Canvas) {
			showSystemUI();
		}
		super.openOptionsMenu();
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {		// 不是
		if (keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
			showExitConfirmation();
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {		// 不是
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {		// 不是
		if ((keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
				&& (event.getFlags() & (KeyEvent.FLAG_LONG_PRESS | KeyEvent.FLAG_CANCELED)) == 0) {
			openOptionsMenu();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		// Intentionally overridden by empty due to support for back-key remapping.
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		MenuInflater inflater = getMenuInflater();
		if (current == null) {
			inflater.inflate(R.menu.midlet_displayable, menu);
			return true;
		}
		boolean hasCommands = current.countCommands() > 0;
		Menu group;
		if (hasCommands) {
			inflater.inflate(R.menu.midlet_common, menu);
			group = menu.getItem(0).getSubMenu();
		} else {
			group = menu;
		}
		inflater.inflate(R.menu.midlet_displayable, group);
		if (current instanceof Canvas) {
			if (actionBarEnabled) {
				inflater.inflate(R.menu.midlet_canvas, menu);
			} else {
				inflater.inflate(R.menu.midlet_canvas_no_bar, group);
			}
			if (inputMethodManager == null) {
				menu.findItem(R.id.action_ime_keyboard).setVisible(false);
			}
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null) {
				inflater.inflate(R.menu.midlet_vk, group);
				if (vk.getLayoutEditMode() == VirtualKeyboard.LAYOUT_EOF) {
					menu.findItem(R.id.action_layout_edit_finish).setVisible(false);
				}
			}
		}
		if (!hasCommands) {
			return true;
		}
		for (Command cmd : current.getCommands()) {
			menu.add(Menu.NONE, cmd.hashCode(), Menu.NONE, cmd.getAndroidLabel());
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (item.getGroupId() == R.id.action_group_common_settings) {
			if (id == R.id.action_ime_keyboard) {
				inputMethodManager.toggleSoftInputFromWindow(
						layout.getWindowToken(),
						InputMethodManager.SHOW_FORCED, 0);
			} else if (id == R.id.action_exit_midlet) {
				showExitConfirmation();
			} else if (id == R.id.action_take_screenshot) {
				takeScreenshot();
			} else if (id == R.id.action_save_log) {
				saveLog();
			} else if (id == R.id.action_limit_fps){
				showLimitFpsDialog();
			} else if (ContextHolder.getVk() != null) {
				// Handled only when virtual keyboard is enabled
				handleVkOptions(id);
			}
			return true;
		}
		if (current != null) {
			return current.menuItemSelected(id);
		}

		return super.onOptionsItemSelected(item);
	}

	private void handleVkOptions(int id) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (id == R.id.action_layout_edit_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_KEYS);
			Toast.makeText(this, R.string.layout_edit_mode,
					Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_scale_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_SCALES);
			Toast.makeText(this, R.string.layout_scale_mode,
					Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_edit_finish) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
			Toast.makeText(this, R.string.layout_edit_finished,
					Toast.LENGTH_SHORT).show();
			showSaveVkAlert(false);
		} else if (id == R.id.action_layout_switch) {
			showSetLayoutDialog();
		} else if (id == R.id.action_hide_buttons) {
			showHideButtonDialog();
		}
	}

	@SuppressLint("CheckResult")
	private void takeScreenshot() {
		microLoader.takeScreenshot((Canvas) current, new SingleObserver<String>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				Toast.makeText(MicroActivity.this, getString(R.string.screenshot_saved)
						+ " " + s, Toast.LENGTH_LONG).show();
			}

			@Override
			public void onError(@NonNull Throwable e) {
				e.printStackTrace();
				Toast.makeText(MicroActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void saveLog() {
		try {
			LogUtils.writeLog();
			Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		boolean[] states = vk.getKeysVisibility();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(vk.getKeyNames(), states, null)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					ListView lv = ((AlertDialog) dialog).getListView();
					SparseBooleanArray current = lv.getCheckedItemPositions();
					for (int i = 0; i < current.size(); i++) {
						if (states[current.keyAt(i)] != current.valueAt(i)) {
							vk.setKeysVisibility(current);
							showSaveVkAlert(true);
							return;
						}
					}
				});
		builder.show();
	}

	private void showSaveVkAlert(boolean keepScreenPreferred) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.CONFIRMATION_REQUIRED);
		builder.setMessage(R.string.pref_vk_save_alert);
		builder.setNegativeButton(android.R.string.no, null);
		AlertDialog dialog = builder.create();

		final VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.isPhone()) {
			AppCompatCheckBox cb = new AppCompatCheckBox(this);
			cb.setText(R.string.opt_save_screen_params);
			cb.setChecked(keepScreenPreferred);

			TypedValue out = new TypedValue();
			getTheme().resolveAttribute(R.attr.dialogPreferredPadding, out, true);
			int paddingH = getResources().getDimensionPixelOffset(out.resourceId);
			int paddingT = getResources().getDimensionPixelOffset(R.dimen.abc_dialog_padding_top_material);
			dialog.setView(cb, paddingH, paddingT, paddingH, 0);

			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) -> {
				if (cb.isChecked()) {
					vk.saveScreenParams();
				}
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
			});
		} else {
			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) ->
					ContextHolder.getVk().onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM));
		}
		dialog.show();
	}

	private void showSetLayoutDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.layout_switch)
				.setSingleChoiceItems(R.array.PREF_VK_TYPE_ENTRIES, vk.getLayout(), null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					vk.setLayout(((AlertDialog) d).getListView().getCheckedItemPosition());
					if (vk.isPhone()) {
						setOrientation(ORIENTATION_PORTRAIT);
					} else {
						setOrientation(microLoader.getOrientation());
					}
				});
		builder.show();
	}

	private void showLimitFpsDialog(){
		EditText editText = new EditText(this);
		editText.setHint(R.string.unlimited);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
		editText.setMaxLines(1);
		editText.setSingleLine(true);
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_LIMIT_FPS)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					Editable text = editText.getText();
					int fps = 0;
					try {
						fps = TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text.toString().trim());
					} catch (NumberFormatException ignored) {
					}
					microLoader.setLimitFps(fps);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.reset, ((d, which) -> microLoader.setLimitFps(-1)))
				.show();
	}

	@Override
	public boolean onContextItemSelected(@NonNull MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		} else if (current instanceof List) {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			((List) current).contextMenuItemSelected(item, info.position);
		}

		return super.onContextItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		ContextHolder.notifyOnActivityResult(requestCode, resultCode, data);
	}
}
