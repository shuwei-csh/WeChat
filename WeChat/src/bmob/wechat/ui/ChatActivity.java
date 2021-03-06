package bmob.wechat.ui;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

import bmob.wechat.adapter.EmoViewPagerAdapter;
import bmob.wechat.adapter.EmoteAdapter;
import bmob.wechat.adapter.MessageChatAdapter;
import bmob.wechat.adapter.NewRecordPlayClickListener;
import bmob.wechat.bean.FaceText;
import bmob.wechat.config.BmobConstants;
import bmob.wechat.init.MyMessageReceiver;
import bmob.wechat.ui.view.DialogTips;
import bmob.wechat.ui.view.EmoticonsEditText;
import bmob.wechat.utils.CommonUtils;
import bmob.wechat.utils.FaceTextUtils;
import bmob.wechat.view.xlist.XListView;
import bmob.wechat.view.xlist.XListView.IXListViewListener;
import cn.bmob.im.BmobChat;
import cn.bmob.im.BmobChatManager;
import cn.bmob.im.BmobNotifyManager;
import cn.bmob.im.BmobRecordManager;
import cn.bmob.im.bean.BmobChatUser;
import cn.bmob.im.bean.BmobInvitation;
import cn.bmob.im.bean.BmobMsg;
import cn.bmob.im.config.BmobConfig;
import cn.bmob.im.db.BmobDB;
import cn.bmob.im.inteface.EventListener;
import cn.bmob.im.inteface.OnRecordChangeListener;
import cn.bmob.im.inteface.UploadListener;
import cn.bmob.im.util.BmobLog;
import cn.bmob.v3.listener.PushListener;
import com.wechat.R;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ChatActivity extends ActivityBase implements OnClickListener,
		IXListViewListener, EventListener {
	// 录音管理类 负责录音和存储
	private BmobRecordManager recordManager;
	// 聊天内容适配器
	private MessageChatAdapter mAdapter;
	// 目标（对方）聊天对象的id
	private String targetId = "";
	// 目标（对方）聊天对象
	private BmobChatUser targetUser;
	// 依次为心情、发送、添加、带键盘图标、长按说话、带声音图标按钮，下面通过逻辑分析动态地改变其显示与否
	private Button btn_chat_emo, btn_chat_send, btn_chat_add,
			btn_chat_keyboard, btn_speak, btn_chat_voice;
	// 对话刷新列表
	private XListView mListView;
	// 文本输入框
	private EmoticonsEditText edit_user_comment;
	// 初步估计聊天消息的页数
	private static int MsgPagerNum;
	// 聊天界面的底部布局，表情布局，添加照片，拍摄布局
	private LinearLayout layout_more, layout_emo, layout_add;
	// 表情布局的左右滑动ViewPager
	private ViewPager pager_emo;
	// 照片，拍摄
	private TextView tv_picture, tv_camera;
	// 使用语音时，弹出来的语音图标布局
	private RelativeLayout layout_record;
	// 使用语音过程中的提示文字
	private TextView tv_voice_tips;
	// 使用语音过程中的提示图片
	private ImageView iv_record;
	// 话筒动画数组
	private Drawable[] drawable_Anims;// 话筒动画
	private static final String ACTIVITY_TAG = "ChatActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chat);
		// 启动自动检测服务，每隔1秒从bmob后台拿消息
		BmobChat.getInstance(this).startPollService(1);
		// BmobChatManager聊天管理类-用于管理聊天：包括发送（聊天消息、Tag消息）、保存消息、绑定用户等
		manager = BmobChatManager.getInstance(this);
		// 初步估计聊天消息的页数
		MsgPagerNum = 0;
		// 组装聊天对象
		// BmobChatUser：自定义BmobUser，添加关联字段：nick（昵称）、avatar（头像）、installId（设备id）、
		// deviceType(设备类型)、blacklist（黑名单）、contacts（好友列表）
		targetUser = (BmobChatUser) getIntent().getSerializableExtra("user");
		targetId = targetUser.getObjectId();
		Toast.makeText(this, "聊天对象为：" + targetUser.getUsername(),
				Toast.LENGTH_LONG).show();
		// BmobLog.i("聊天对象：" + targetUser.getUsername() + ",targetId = "
		// + targetId);
		// 注册广播接收器
		initNewMessageBroadCast();
		initView();
	}

	/**
	 * 注册接收消息广播
	 */
	private void initNewMessageBroadCast() {
		// 注册接收消息广播
		receiver = new NewBroadcastReceiver();
		IntentFilter intentFilter = new IntentFilter(
				BmobConfig.BROADCAST_NEW_MESSAGE);
		// 设置广播的优先级别大于Mainacitivity,这样如果消息来的时候正好在chat页面，
		// 直接显示消息，而不是提示消息未读
		intentFilter.setPriority(5);
		registerReceiver(receiver, intentFilter);
	}

	/**
	 * 新消息广播接收者
	 * 
	 */
	private class NewBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String from = intent.getStringExtra("fromId");
			String msgId = intent.getStringExtra("msgId");
			String msgTime = intent.getStringExtra("msgTime");
			// 收到这个广播的时候，message已经在消息表中，可直接获取
			BmobMsg msg = BmobChatManager.getInstance(ChatActivity.this)
					.getMessage(msgId, msgTime);
			if (!from.equals(targetId))// 如果不是当前正在聊天对象的消息，不处理
				return;
			// 添加到当前页面
			mAdapter.add(msg);
			// 定位
			mListView.setSelection(mAdapter.getCount() - 1);
			// 取消当前聊天对象的未读标示
			BmobDB.create(ChatActivity.this).resetUnread(targetId);
			// 记得把广播给终结掉
			abortBroadcast();
		}
	}

	private void initView() {
		mListView = (XListView) findViewById(R.id.mListView);
		// initTopBarForLeft("与" + targetUser.getUsername() + "对话");
		// 初始化底部输入框那部分的视图
		initBottomView();
		initXListView();
		initVoiceView();
	}

	/**
	 * 初始化底部输入框那部分的视图
	 */

	private void initBottomView() {
		// 最左边
		btn_chat_add = (Button) findViewById(R.id.btn_chat_add);
		btn_chat_emo = (Button) findViewById(R.id.btn_chat_emo);
		btn_chat_add.setOnClickListener(this);
		btn_chat_emo.setOnClickListener(this);
		// 最右边
		btn_chat_keyboard = (Button) findViewById(R.id.btn_chat_keyboard);
		btn_chat_voice = (Button) findViewById(R.id.btn_chat_voice);
		btn_chat_voice.setOnClickListener(this);
		btn_chat_keyboard.setOnClickListener(this);
		btn_chat_send = (Button) findViewById(R.id.btn_chat_send);
		btn_chat_send.setOnClickListener(this);
		// 最下面
		layout_more = (LinearLayout) findViewById(R.id.layout_more);
		layout_emo = (LinearLayout) findViewById(R.id.layout_emo);
		layout_add = (LinearLayout) findViewById(R.id.layout_add);
		initAddView();
		initEmoView();

		// 最中间
		// 语音框
		btn_speak = (Button) findViewById(R.id.btn_speak);
		// 输入框
		edit_user_comment = (EmoticonsEditText) findViewById(R.id.edit_user_comment);
		edit_user_comment.setOnClickListener(this);
		edit_user_comment.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				// TODO Auto-generated method stub
				if (!TextUtils.isEmpty(s)) {
					// 如果文本框里面的内容s不为 null or 0-length.就让发送按钮可见
					btn_chat_send.setVisibility(View.VISIBLE);
					btn_chat_keyboard.setVisibility(View.GONE);
					btn_chat_voice.setVisibility(View.GONE);
				} else {
					if (btn_chat_voice.getVisibility() != View.VISIBLE) {
						btn_chat_voice.setVisibility(View.VISIBLE);
						btn_chat_send.setVisibility(View.GONE);
						btn_chat_keyboard.setVisibility(View.GONE);
					}
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub

			}

			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub
			}
		});

	}

	/**
	 * 初始化有图片、拍摄、位置的add视图
	 */
	private void initAddView() {
		tv_picture = (TextView) findViewById(R.id.tv_picture);
		tv_camera = (TextView) findViewById(R.id.tv_camera);

		tv_picture.setOnClickListener(this);

		tv_camera.setOnClickListener(this);
	}

	/**
	 * 初始化表情布局
	 * 
	 * @Title: initEmoView
	 * @Description: TODO
	 * @param
	 * @return void
	 * @throws
	 */
	List<FaceText> emos;

	private void initEmoView() {
		pager_emo = (ViewPager) findViewById(R.id.pager_emo);
		emos = FaceTextUtils.faceTexts;

		List<View> views = new ArrayList<View>();
		for (int i = 0; i < 2; ++i) {
			views.add(getGridView(i));
		}
		pager_emo.setAdapter(new EmoViewPagerAdapter(views));
	}

	private View getGridView(final int i) {
		View view = View.inflate(this, R.layout.include_emo_gridview, null);
		GridView gridview = (GridView) view.findViewById(R.id.gridview);
		List<FaceText> list = new ArrayList<FaceText>();
		if (i == 0) {
			list.addAll(emos.subList(0, 21));
		} else if (i == 1) {
			list.addAll(emos.subList(21, emos.size()));
		}
		final EmoteAdapter gridAdapter = new EmoteAdapter(ChatActivity.this,
				list);
		gridview.setAdapter(gridAdapter);
		gridview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				FaceText name = (FaceText) gridAdapter.getItem(position);
				String key = name.text.toString();
				try {
					if (edit_user_comment != null && !TextUtils.isEmpty(key)) {
						int start = edit_user_comment.getSelectionStart();
						CharSequence content = edit_user_comment.getText()
								.insert(start, key);
						edit_user_comment.setText(content);
						// 定位光标位置
						CharSequence info = edit_user_comment.getText();
						// Spannable
						if (info instanceof Spannable) {
							Spannable spanText = (Spannable) info;
							Selection.setSelection(spanText,
									start + key.length());
						}
					}
				} catch (Exception e) {

				}

			}
		});
		return view;
	}

	/**
	 * 加载消息历史，从数据库中读出
	 */
	private List<BmobMsg> initMsgData() {
		List<BmobMsg> list = BmobDB.create(this).queryMessages(targetId,
				MsgPagerNum);
		return list;
	}

	/**
	 * 界面刷新
	 * 
	 * @Title: initOrRefresh
	 * @Description: TODO
	 * @param
	 * @return void
	 * @throws
	 */
	private void initOrRefresh() {
		if (mAdapter != null) {
			if (MyMessageReceiver.mNewNum != 0) {// 用于更新当在聊天界面锁屏期间来了消息，这时再回到聊天页面的时候需要显示新来的消息
				int news = MyMessageReceiver.mNewNum;// 有可能锁屏期间，来了N条消息,因此需要倒叙显示在界面上
				int size = initMsgData().size();
				for (int i = (news - 1); i >= 0; i--) {
					mAdapter.add(initMsgData().get(size - (i + 1)));// 添加最后一条消息到界面显示
				}
				mListView.setSelection(mAdapter.getCount() - 1);
			} else {
				mAdapter.notifyDataSetChanged();
			}
		} else {
			mAdapter = new MessageChatAdapter(this, initMsgData());
			mListView.setAdapter(mAdapter);
		}
	}

	private void initXListView() {
		// 首先不允许加载更多
		mListView.setPullLoadEnable(false);
		// 允许下拉
		mListView.setPullRefreshEnable(true);
		// 设置监听器
		mListView.setXListViewListener(this);
		mListView.pullRefreshing();
		mListView.setDividerHeight(0);
		// 加载数据
		initOrRefresh();
		mListView.setSelection(mAdapter.getCount() - 1);
		mListView.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				// TODO Auto-generated method stub
				hideSoftInputView();
				layout_more.setVisibility(View.GONE);
				layout_add.setVisibility(View.GONE);
				btn_chat_voice.setVisibility(View.VISIBLE);
				btn_chat_keyboard.setVisibility(View.GONE);
				btn_chat_send.setVisibility(View.GONE);
				return false;
			}
		});

		// 重发按钮的点击事件
		mAdapter.setOnInViewClickListener(R.id.iv_fail_resend,
				new MessageChatAdapter.onInternalClickListener() {

					@Override
					public void OnClickListener(View parentV, View v,
							Integer position, Object values) {
						// 重发消息
						showResendDialog(parentV, v, values);
					}
				});
	}

	/**
	 * 显示重发按钮 showResendDialog
	 * 
	 * @Title: showResendDialog
	 * @Description: TODO
	 * @param @param recent
	 * @return void
	 * @throws
	 */
	public void showResendDialog(final View parentV, View v, final Object values) {
		DialogTips dialog = new DialogTips(this, "确定重发该消息", "确定", "取消", "提示",
				true);
		// 设置成功事件
		dialog.SetOnSuccessListener(new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogInterface, int userId) {
				if (((BmobMsg) values).getMsgType() == BmobConfig.TYPE_IMAGE
						|| ((BmobMsg) values).getMsgType() == BmobConfig.TYPE_VOICE) {// 图片和语音类型的采用
					resendFileMsg(parentV, values);
				} else {
					resendTextMsg(parentV, values);
				}
				dialogInterface.dismiss();
			}
		});
		// 显示确认对话框
		dialog.show();
		dialog = null;
	}

	/**
	 * 重发文本消息
	 */
	private void resendTextMsg(final View parentV, final Object values) {
		BmobChatManager.getInstance(ChatActivity.this).resendTextMessage(
				targetUser, (BmobMsg) values, new PushListener() {

					@Override
					public void onSuccess() {
						// TODO Auto-generated method stub
						ShowLog("发送成功");
						((BmobMsg) values)
								.setStatus(BmobConfig.STATUS_SEND_SUCCESS);
						parentV.findViewById(R.id.progress_load).setVisibility(
								View.INVISIBLE);
						parentV.findViewById(R.id.iv_fail_resend)
								.setVisibility(View.INVISIBLE);
						parentV.findViewById(R.id.tv_send_status)
								.setVisibility(View.VISIBLE);
						((TextView) parentV.findViewById(R.id.tv_send_status))
								.setText("已发送");
					}

					@Override
					public void onFailure(int arg0, String arg1) {
						// TODO Auto-generated method stub
						ShowLog("发送失败:" + arg1);
						((BmobMsg) values)
								.setStatus(BmobConfig.STATUS_SEND_FAIL);
						parentV.findViewById(R.id.progress_load).setVisibility(
								View.INVISIBLE);
						parentV.findViewById(R.id.iv_fail_resend)
								.setVisibility(View.VISIBLE);
						parentV.findViewById(R.id.tv_send_status)
								.setVisibility(View.INVISIBLE);
					}
				});
		mAdapter.notifyDataSetChanged();
	}

	/**
	 * 重发图片消息
	 * 
	 * @Title: resendImageMsg
	 * @Description: TODO
	 * @param @param parentV
	 * @param @param values
	 * @return void
	 * @throws
	 */
	private void resendFileMsg(final View parentV, final Object values) {
		BmobChatManager.getInstance(ChatActivity.this).resendFileMessage(
				targetUser, (BmobMsg) values, new UploadListener() {

					@Override
					public void onStart(BmobMsg msg) {
						// TODO Auto-generated method stub
					}

					@Override
					public void onSuccess() {
						// TODO Auto-generated method stub
						((BmobMsg) values)
								.setStatus(BmobConfig.STATUS_SEND_SUCCESS);
						parentV.findViewById(R.id.progress_load).setVisibility(
								View.INVISIBLE);
						parentV.findViewById(R.id.iv_fail_resend)
								.setVisibility(View.INVISIBLE);
						if (((BmobMsg) values).getMsgType() == BmobConfig.TYPE_VOICE) {
							parentV.findViewById(R.id.tv_send_status)
									.setVisibility(View.GONE);
							parentV.findViewById(R.id.tv_voice_length)
									.setVisibility(View.VISIBLE);
						} else {
							parentV.findViewById(R.id.tv_send_status)
									.setVisibility(View.VISIBLE);
							((TextView) parentV
									.findViewById(R.id.tv_send_status))
									.setText("已发送");
						}
					}

					@Override
					public void onFailure(int arg0, String arg1) {
						// TODO Auto-generated method stub
						((BmobMsg) values)
								.setStatus(BmobConfig.STATUS_SEND_FAIL);
						parentV.findViewById(R.id.progress_load).setVisibility(
								View.INVISIBLE);
						parentV.findViewById(R.id.iv_fail_resend)
								.setVisibility(View.VISIBLE);
						parentV.findViewById(R.id.tv_send_status)
								.setVisibility(View.INVISIBLE);
					}
				});
		mAdapter.notifyDataSetChanged();
	}

	/**
	 * 初始化语音布局
	 * 
	 * @Title: initVoiceView
	 * @Description: TODO
	 * @param
	 * @return void
	 * @throws
	 */
	private void initVoiceView() {
		layout_record = (RelativeLayout) findViewById(R.id.layout_record);
		tv_voice_tips = (TextView) findViewById(R.id.tv_voice_tips);
		iv_record = (ImageView) findViewById(R.id.iv_record);
		// VoiceTouchListen()长按说话的监听器
		btn_speak.setOnTouchListener(new VoiceTouchListen());
		// 初始化语音动画资源
		initVoiceAnimRes();
		// 语音相关管理器 BmobRecordManager:录音管理类 负责录音和存储
		initRecordManager();
	}

	/**
	 * 长按说话
	 * 
	 * @ClassName: VoiceTouchListen
	 * @Description: TODO
	 * @author smile
	 * @date 2014-7-1 下午6:10:16
	 */
	class VoiceTouchListen implements View.OnTouchListener {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (!CommonUtils.checkSdCard()) {
					ShowToast("发送语音需要sdcard支持！");
					return false;
				}
				try {
					v.setPressed(true);
					layout_record.setVisibility(View.VISIBLE);
					tv_voice_tips
							.setText(getString(R.string.voice_cancel_tips));
					// 开始录音
					recordManager.startRecording(targetId);
				} catch (Exception e) {
				}
				return true;
				// 如果按下的动作有移动
			case MotionEvent.ACTION_MOVE: {
				// getX()是表示Widget相对于自身左上角的x坐标，getY()同样的道理
				if (event.getY() < 0) {
					tv_voice_tips
							.setText(getString(R.string.voice_cancel_tips));
					tv_voice_tips.setTextColor(Color.RED);
				} else {
					// 录音后手指向上滑动，一样也可以取消发送，因为压根就没有调用发送的方法，
					// 只有当手指离开了按钮ACTION_UP这一事件才会发送。
					tv_voice_tips.setText(getString(R.string.voice_up_tips));
					tv_voice_tips.setTextColor(Color.WHITE);
				}
				return true;
			}
			case MotionEvent.ACTION_UP:
				v.setPressed(false);
				layout_record.setVisibility(View.INVISIBLE);
				try {
					if (event.getY() < 0) {// 放弃录音
						recordManager.cancelRecording();
						BmobLog.i("voice", "放弃发送语音");
					} else {
						int recordTime = recordManager.stopRecording();
						if (recordTime > 1) {
							// 发送语音文件
							BmobLog.i("voice", "发送语音");
							// getRecordFilePath获取录音文件的存储地址
							// targetId：@param - chatObjectId:聊天的对象Id
							sendVoiceMessage(
									// 这里的targetId是指自己的ID，还是对方的ID?
									recordManager.getRecordFilePath(targetId),
									recordTime);
						} else {// 录音时间过短，则提示录音过短的提示
							layout_record.setVisibility(View.GONE);
							showShortToast().show();
						}
					}
				} catch (Exception e) {
					// TODO: handle exception
				}
				return true;
			default:
				return false;
			}
		}
	}

	/**
	 * 发送语音消息
	 * 
	 * @Title: sendImageMessage
	 * @Description: TODO
	 * @param @param localPath
	 * @return void
	 * @throws
	 */
	private void sendVoiceMessage(String local, int length) {
		// 这里的targetUser是指BmobChatUser对象
		manager.sendVoiceMessage(targetUser, local, length,
				new UploadListener() {

					@Override
					public void onStart(BmobMsg msg) {
						// TODO Auto-generated method stub
						refreshMessage(msg);
					}

					@Override
					public void onSuccess() {
						// TODO Auto-generated method stub
						mAdapter.notifyDataSetChanged();
					}

					@Override
					public void onFailure(int error, String arg1) {
						// TODO Auto-generated method stub
						ShowLog("上传语音失败 -->arg1：" + arg1);
						mAdapter.notifyDataSetChanged();
					}
				});
	}

	Toast toast;

	/**
	 * 显示录音时间过短的Toast
	 * 
	 * @Title: showShortToast
	 * @return void
	 * @throws
	 */
	private Toast showShortToast() {
		if (toast == null) {
			toast = new Toast(this);
		}
		View view = LayoutInflater.from(this).inflate(
				R.layout.include_chat_voice_short, null);
		toast.setView(view);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(50);
		return toast;
	}

	/**
	 * 初始化语音动画资源
	 * 
	 * @Title: initVoiceAnimRes
	 * @Description: TODO
	 * @param
	 * @return void
	 * @throws
	 */
	private void initVoiceAnimRes() {
		drawable_Anims = new Drawable[] {
				getResources().getDrawable(R.drawable.chat_icon_voice2),
				getResources().getDrawable(R.drawable.chat_icon_voice3),
				getResources().getDrawable(R.drawable.chat_icon_voice4),
				getResources().getDrawable(R.drawable.chat_icon_voice5),
				getResources().getDrawable(R.drawable.chat_icon_voice6) };
	}

	/**
	 * 语音相关管理器 BmobRecordManager:录音管理类 负责录音和存储
	 */
	private void initRecordManager() {
		recordManager = BmobRecordManager.getInstance(this);
		// 设置音量大小监听--在这里开发者可以自己实现：当剩余10秒情况下的给用户的提示，类似微信的语音那样
		recordManager.setOnRecordChangeListener(new OnRecordChangeListener() {
			// 音量的变化
			@Override
			public void onVolumnChanged(int value) {
				// TODO Auto-generated method stub
				// 音量的变化通过变换图片来体现，drawable_Anims[]已在上面初始化语音动画资源
				iv_record.setImageDrawable(drawable_Anims[value]);
			}

			// 录音时间监听
			@Override
			public void onTimeChanged(int recordTime, String localPath) {
				// TODO Auto-generated method stub
				BmobLog.i("voice", "已录音长度:" + recordTime);
				if (recordTime >= BmobRecordManager.MAX_RECORD_TIME) {// 1分钟结束，发送消息
					// 需要重置按钮
					btn_speak.setPressed(false);
					btn_speak.setClickable(false);
					// 取消录音框
					layout_record.setVisibility(View.INVISIBLE);
					// 发送语音消息
					sendVoiceMessage(localPath, recordTime);
					// 是为了防止过了录音时间后，会多发一条语音出去的情况。
					handler.postDelayed(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							btn_speak.setClickable(true);
						}
					}, 1000);
				} else {

				}
			}
		});
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
		case R.id.edit_user_comment:// 点击文本输入框
			mListView.setSelection(mListView.getCount() - 1);
			if (layout_more.getVisibility() == View.VISIBLE) {
				layout_add.setVisibility(View.GONE);
				layout_emo.setVisibility(View.GONE);
				layout_more.setVisibility(View.GONE);
			}
			break;
		case R.id.btn_chat_emo:// 点击笑脸图标
			Log.i(ChatActivity.ACTIVITY_TAG, "单击聊天窗口的表情按钮");
			if (layout_more.getVisibility() == View.GONE) {
				// 根据是否点击笑脸，来显示文本输入框的状态，是否显示表情栏
				Log.i(ChatActivity.ACTIVITY_TAG, "显示正常输入");
				showEditState(true);
			} else {
				if (layout_add.getVisibility() == View.VISIBLE) {
					layout_add.setVisibility(View.GONE);
					layout_emo.setVisibility(View.VISIBLE);
					Log.i(ChatActivity.ACTIVITY_TAG, "显示表情");
				} else {
					layout_more.setVisibility(View.GONE);
				}
			}

			break;
		case R.id.btn_chat_add:// 添加按钮-显示图片、拍照、位置
			if (layout_more.getVisibility() == View.GONE) {
				layout_more.setVisibility(View.VISIBLE);
				layout_add.setVisibility(View.VISIBLE);
				layout_emo.setVisibility(View.GONE);
				hideSoftInputView();
			} else {
				if (layout_emo.getVisibility() == View.VISIBLE) {
					layout_emo.setVisibility(View.GONE);
					layout_add.setVisibility(View.VISIBLE);
				} else {
					layout_more.setVisibility(View.GONE);
				}
			}

			break;
		case R.id.btn_chat_voice:// 语音按钮
			edit_user_comment.setVisibility(View.GONE);
			layout_more.setVisibility(View.GONE);
			btn_chat_voice.setVisibility(View.GONE);
			btn_chat_keyboard.setVisibility(View.VISIBLE);
			btn_speak.setVisibility(View.VISIBLE);
			hideSoftInputView();
			break;
		case R.id.btn_chat_keyboard:// 键盘按钮，点击就弹出键盘并隐藏掉声音按钮
			showEditState(false);
			break;
		case R.id.btn_chat_send:// 发送文本
			final String msg = edit_user_comment.getText().toString();
			if (msg.equals("")) {
				ShowToast("请输入发送消息!");
				return;
			}
			boolean isNetConnected = CommonUtils.isNetworkAvailable(this);
			if (!isNetConnected) {
				ShowToast(R.string.network_tips);
				// return;
			}
			// 组装BmobMessage对象
			BmobMsg message = BmobMsg.createTextSendMsg(this, targetId, msg);
			// 默认发送完成，将数据保存到本地消息表和最近会话表中
			// sendTextMessage()给指定用户发送文本类型的消息：包括文字加表情和位置消息
			// 默认推送成功之后存储消息到数据库（本地和Bmob）
			manager.sendTextMessage(targetUser, message);
			// 刷新界面
			refreshMessage(message);

			break;
		case R.id.tv_camera:// 拍照
			selectImageFromCamera();
			break;
		case R.id.tv_picture:// 图片
			selectImageFromLocal();
			break;

		default:
			break;
		}
	}

	private String localCameraPath = "";// 拍照后得到的图片地址

	/**
	 * 启动相机拍照 startCamera
	 * 
	 * @Title: startCamera
	 * @throws
	 */
	public void selectImageFromCamera() {
		Intent openCameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		// BmobConstants.BMOB_PICTURE_PATH存放发送图片的目录
		File dir = new File(BmobConstants.BMOB_PICTURE_PATH);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File file = new File(dir, String.valueOf(System.currentTimeMillis())
				+ ".jpg");
		localCameraPath = file.getPath();
		Uri imageUri = Uri.fromFile(file);
		openCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
		startActivityForResult(openCameraIntent,
				BmobConstants.REQUESTCODE_TAKE_CAMERA);
	}

	/**
	 * 选择图片
	 * 
	 * @Title: selectImage
	 * @Description: TODO
	 * @param
	 * @return void
	 * @throws
	 */
	public void selectImageFromLocal() {
		Intent intent;
		if (Build.VERSION.SDK_INT < 19) {
			intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
		} else {
			intent = new Intent(
					Intent.ACTION_PICK,
					android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		}
		startActivityForResult(intent, BmobConstants.REQUESTCODE_TAKE_LOCAL);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case BmobConstants.REQUESTCODE_TAKE_CAMERA:// 当取到值的时候才上传path路径下的图片到服务器
				ShowLog("本地图片的地址：" + localCameraPath);
				// 调用sendImageMessage()方法发送图片
				sendImageMessage(localCameraPath);
				break;
			case BmobConstants.REQUESTCODE_TAKE_LOCAL:
				if (data != null) {
					Uri selectedImage = data.getData();
					if (selectedImage != null) {
						Cursor cursor = getContentResolver().query(
								selectedImage, null, null, null, null);
						cursor.moveToFirst();
						int columnIndex = cursor.getColumnIndex("_data");
						String localSelectPath = cursor.getString(columnIndex);
						cursor.close();
						if (localSelectPath == null
								|| localSelectPath.equals("null")) {
							ShowToast("找不到您想要的图片");
							return;
						}
						// 调用sendImageMessage()方法发送图片
						sendImageMessage(localSelectPath);
					}
				}
				break;

			}
		}
	}

	/**
	 * 默认先上传本地图片，之后才显示出来 sendImageMessage
	 * 
	 * @Title: sendImageMessage
	 * @Description: TODO
	 * @param @param localPath
	 * @return void
	 * @throws
	 */
	private void sendImageMessage(String local) {
		if (layout_more.getVisibility() == View.VISIBLE) {
			layout_more.setVisibility(View.GONE);
			layout_add.setVisibility(View.GONE);
			layout_emo.setVisibility(View.GONE);
		}
		// 这里targetUser指对方的BmobChatUser对象
		manager.sendImageMessage(targetUser, local, new UploadListener() {

			@Override
			public void onStart(BmobMsg msg) {
				// TODO Auto-generated method stub
				ShowLog("开始上传onStart：" + msg.getContent() + ",状态："
						+ msg.getStatus());
				refreshMessage(msg);
			}

			@Override
			public void onSuccess() {
				// TODO Auto-generated method stub
				mAdapter.notifyDataSetChanged();
			}

			@Override
			public void onFailure(int error, String arg1) {
				// TODO Auto-generated method stub
				ShowLog("上传失败 -->arg1：" + arg1);
				mAdapter.notifyDataSetChanged();
			}
		});
	}

	/**
	 * 根据是否点击笑脸，来显示文本输入框的状态，是否显示表情栏
	 * 
	 * @Title: showEditState
	 * @Description: TODO
	 * @param @param isEmo: 用于区分文字和表情
	 * @return void
	 * @throws
	 */
	private void showEditState(boolean isEmo) {
		edit_user_comment.setVisibility(View.VISIBLE);
		btn_chat_keyboard.setVisibility(View.GONE);
		btn_chat_voice.setVisibility(View.VISIBLE);
		btn_speak.setVisibility(View.GONE);
		edit_user_comment.requestFocus();
		if (isEmo) {
			layout_more.setVisibility(View.VISIBLE);
			layout_more.setVisibility(View.VISIBLE);
			layout_emo.setVisibility(View.VISIBLE);
			layout_add.setVisibility(View.GONE);
			hideSoftInputView();// 隐藏软键盘
		} else {
			layout_more.setVisibility(View.GONE);
			showSoftInputView();// 显示软键盘
		}
	}

	// 显示软键盘
	public void showSoftInputView() {
		if (getWindow().getAttributes().softInputMode == WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) {
			if (getCurrentFocus() != null)
				((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
						.showSoftInput(edit_user_comment, 0);
		}
	}

	@SuppressLint("HandlerLeak")
	/**
	 *handler可以分发Message对象和Runnable对象到主线程中, 
	 *每个Handler实例,都会绑定到创建他的线程中(一般是位于主线程
	 */
	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			if (msg.what == NEW_MESSAGE) {
				BmobMsg message = (BmobMsg) msg.obj;
				String uid = message.getBelongId();
				BmobMsg m = BmobChatManager.getInstance(ChatActivity.this)
						.getMessage(message.getConversationId(),
								message.getMsgTime());
				if (!uid.equals(targetId))// 如果不是当前正在聊天对象的消息，不处理
					return;
				mAdapter.add(m);
				// 定位
				mListView.setSelection(mAdapter.getCount() - 1);
				// 取消当前聊天对象的未读标示
				BmobDB.create(ChatActivity.this).resetUnread(targetId);
			}
		}
	};

	public static final int NEW_MESSAGE = 0x001;// 收到消息

	NewBroadcastReceiver receiver;

	/**
	 * 刷新界面
	 * 
	 * @Title: refreshMessage
	 * @Description: TODO
	 * @param @param message
	 * @return void
	 * @throws
	 */
	private void refreshMessage(BmobMsg msg) {
		// 更新界面
		mAdapter.add(msg);
		mListView.setSelection(mAdapter.getCount() - 1);
		edit_user_comment.setText("");
	}

	@Override
	public void onMessage(BmobMsg message) {
		// TODO Auto-generated method stub
		Message handlerMsg = handler.obtainMessage(NEW_MESSAGE);
		handlerMsg.obj = message;
		handler.sendMessage(handlerMsg);
	}

	@Override
	public void onNetChange(boolean isNetConnected) {
		// TODO Auto-generated method stub
		if (!isNetConnected) {
			ShowToast(R.string.network_tips);
		}
	}

	@Override
	public void onAddUser(BmobInvitation invite) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onOffline() {
		// TODO Auto-generated method stub
		showOfflineDialog(this);
	}

	@Override
	public void onReaded(String conversionId, String msgTime) {
		// TODO Auto-generated method stub
		// 此处应该过滤掉不是和当前用户的聊天的回执消息界面的刷新
		if (conversionId.split("&")[1].equals(targetId)) {
			// 修改界面上指定消息的阅读状态
			for (BmobMsg msg : mAdapter.getList()) {
				if (msg.getConversationId().equals(conversionId)
						&& msg.getMsgTime().equals(msgTime)) {
					msg.setStatus(BmobConfig.STATUS_SEND_RECEIVERED);
				}
				mAdapter.notifyDataSetChanged();
			}
		}
	}

	public void onRefresh() {
		// TODO Auto-generated method stub
		handler.postDelayed(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				MsgPagerNum++;
				int total = BmobDB.create(ChatActivity.this)
						.queryChatTotalCount(targetId);
				BmobLog.i("记录总数：" + total);
				int currents = mAdapter.getCount();
				if (total <= currents) {
					ShowToast("聊天记录加载完了哦!");
				} else {
					List<BmobMsg> msgList = initMsgData();
					mAdapter.setList(msgList);
					mListView.setSelection(mAdapter.getCount() - currents - 1);
				}
				mListView.stopRefresh();
			}
		}, 1000);
	}

	@Override
	public void onLoadMore() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (layout_more.getVisibility() == 0) {
				layout_more.setVisibility(View.GONE);
				return false;
			} else {
				return super.onKeyDown(keyCode, event);
			}
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		// 新消息到达，重新刷新界面
		initOrRefresh();
		MyMessageReceiver.ehList.add(this);// 监听推送的消息
		// 有可能锁屏期间，在聊天界面出现通知栏，这时候需要清除通知和清空未读消息数
		BmobNotifyManager.getInstance(this).cancelNotify();
		BmobDB.create(this).resetUnread(targetId);
		// 清空消息未读数-这个要在刷新之后
		MyMessageReceiver.mNewNum = 0;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		MyMessageReceiver.ehList.remove(this);// 监听推送的消息
		// 停止录音
		if (recordManager.isRecording()) {
			recordManager.cancelRecording();
			layout_record.setVisibility(View.GONE);
		}
		// 停止播放录音
		if (NewRecordPlayClickListener.isPlaying
				&& NewRecordPlayClickListener.currentPlayListener != null) {
			NewRecordPlayClickListener.currentPlayListener.stopPlayRecord();
		}

	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		hideSoftInputView();
		BmobChat.getInstance(this).stopPollService();
		try {
			unregisterReceiver(receiver);
		} catch (Exception e) {
		}

	}

}