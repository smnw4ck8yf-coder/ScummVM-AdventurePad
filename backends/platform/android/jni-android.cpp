/* ScummVM - Graphic Adventure Engine
 *
 * ScummVM is the legal property of its developers, whose names
 * are too numerous to list here. Please refer to the COPYRIGHT
 * file distributed with this source distribution.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

// Allow use of stuff in <time.h> and abort()
#define FORBIDDEN_SYMBOL_EXCEPTION_time_h
#define FORBIDDEN_SYMBOL_EXCEPTION_abort

// Disable printf override in common/forbidden.h to avoid
// clashes with log.h from the Android SDK.
// That header file uses
//   __attribute__ ((format(printf, 3, 4)))
// which gets messed up by our override mechanism; this could
// be avoided by either changing the Android SDK to use the equally
// legal and valid
//   __attribute__ ((format(__printf__, 3, 4)))
// or by refining our printf override to use a varadic macro
// (which then wouldn't be portable, though).
// Anyway, for now we just disable the printf override globally
// for the Android port
#define FORBIDDEN_SYMBOL_EXCEPTION_printf

#include <android/bitmap.h>
#include <time.h>

#include "backends/platform/android/android.h"
#include "backends/platform/android/adventurepad.h"
#include "backends/platform/android/jni-android.h"
#include "backends/platform/android/asset-archive.h"

#include "backends/networking/basic/android/jni.h"

#include "base/main.h"
#include "base/version.h"
#include "common/config-manager.h"
#include "common/error.h"
#include "common/textconsole.h"
#include "engines/engine.h"
#include "graphics/surface.h"

__attribute__ ((visibility("default")))
jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
	return JNI::onLoad(vm);
}

pthread_key_t JNI::_env_tls;

JavaVM *JNI::_vm = 0;
jobject JNI::_jobj = 0;
jobject JNI::_jobj_egl = 0;
jobject JNI::_jobj_egl_display = 0;
jobject JNI::_jobj_egl_surface = 0;
bool JNI::_have_mirror_surface = false;
int JNI::_mirror_surface_width = 0;
int JNI::_mirror_surface_height = 0;
int64 JNI::_mirror_surface_generation = 0;
int JNI::_mirror_diagnostic_frames_remaining = 0;
int JNI::_egl_version = 0;

Common::Archive *JNI::_asset_archive = 0;
OSystem_Android *JNI::_system = 0;

bool JNI::assets_updated = false;

bool JNI::pause = false;
sem_t JNI::pause_sem;

int JNI::surface_changeid = 0;
int JNI::egl_surface_width = 0;
int JNI::egl_surface_height = 0;
int JNI::egl_bits_per_pixel = 0;
bool JNI::_ready_for_events = 0;
bool JNI::virt_keyboard_state = false;
int32 JNI::gestures_insets[4] = { 0, 0, 0, 0 };
int32 JNI::cutout_insets[4] = { 0, 0, 0, 0 };

jmethodID JNI::_MID_getDPI = 0;
jmethodID JNI::_MID_displayMessageOnOSD = 0;
jmethodID JNI::_MID_openUrl = 0;
jmethodID JNI::_MID_hasTextInClipboard = 0;
jmethodID JNI::_MID_getTextFromClipboard = 0;
jmethodID JNI::_MID_setTextInClipboard = 0;
jmethodID JNI::_MID_isConnectionLimited = 0;
jmethodID JNI::_MID_setWindowCaption = 0;
jmethodID JNI::_MID_showVirtualKeyboard = 0;
jmethodID JNI::_MID_showOnScreenControls = 0;
jmethodID JNI::_MID_setTouchMode = 0;
jmethodID JNI::_MID_getTouchMode = 0;
jmethodID JNI::_MID_setOrientation = 0;
jmethodID JNI::_MID_getScummVMBasePath;
jmethodID JNI::_MID_getScummVMConfigPath;
jmethodID JNI::_MID_getScummVMLogPath;
jmethodID JNI::_MID_setCurrentGame = 0;
jmethodID JNI::_MID_setAdventurePadGameViewport = 0;
jmethodID JNI::_MID_isAdventurePadAdvancedLaunch = 0;
jmethodID JNI::_MID_returnToAdventurePad = 0;
jmethodID JNI::_MID_notifyHTTPService = 0;
jmethodID JNI::_MID_getSysArchives = 0;
jmethodID JNI::_MID_getAllStorageLocations = 0;
jmethodID JNI::_MID_initSurface = 0;
jmethodID JNI::_MID_deinitSurface = 0;
jmethodID JNI::_MID_updateMirrorSurface = 0;
jmethodID JNI::_MID_makeMirrorSurfaceCurrent = 0;
jmethodID JNI::_MID_makePrimarySurfaceCurrent = 0;
jmethodID JNI::_MID_swapMirrorSurface = 0;
jmethodID JNI::_MID_reportMirrorStatus = 0;
jmethodID JNI::_MID_reportMirrorCursor = 0;
jmethodID JNI::_MID_failMirrorSurface = 0;
jmethodID JNI::_MID_reportMirrorSourceGeometry = 0;
jmethodID JNI::_MID_mirrorGeometryGeneration = 0;
jmethodID JNI::_MID_updateMirrorCrop = 0;
jmethodID JNI::_MID_reportMirrorCropAck = 0;
jmethodID JNI::_MID_updateUpperPresentation = 0;
jmethodID JNI::_MID_reportUpperPresentationAck = 0;
jmethodID JNI::_MID_eglVersion = 0;
jmethodID JNI::_MID_getNewSAFTree = 0;
jmethodID JNI::_MID_getSAFTrees = 0;
jmethodID JNI::_MID_findSAFTree = 0;
jmethodID JNI::_MID_exportBackup = 0;
jmethodID JNI::_MID_importBackup = 0;

jmethodID JNI::_MID_EGL10_eglSwapBuffers = 0;
jmethodID JNI::_MID_EGL10_eglGetError = 0;

const JNINativeMethod JNI::_natives[] = {
	{ "create", "(Landroid/content/res/AssetManager;"
				"Ljavax/microedition/khronos/egl/EGL10;"
				"Ljavax/microedition/khronos/egl/EGLDisplay;"
				"Z)V",
		(void *)JNI::create },
	{ "destroy", "()V",
		(void *)JNI::destroy },
	{ "setSurface", "(III)V",
		(void *)JNI::setSurface },
	{ "main", "([Ljava/lang/String;)I",
		(void *)JNI::main },
	{ "pushEvent", "(IIIIIII)V",
		(void *)JNI::pushEvent },
	{ "updateTouch", "(IIII)V",
		(void *)JNI::updateTouch },
	{ "setupTouchMode", "(II)V",
		(void *)JNI::setupTouchMode },
	{ "syncVirtkeyboardState", "(Z)V",
		(void *)JNI::syncVirtkeyboardState },
	{ "setPause", "(Z)V",
		(void *)JNI::setPause },
	{ "systemInsetsUpdated", "([I[I[I)V",
		(void *)JNI::systemInsetsUpdated },
	{ "setDefaultAudioValues", "(II)V",
		(void *)JNI::setDefaultAudioValues },
	{ "notifyAudioDisconnect", "()V",
		(void *)JNI::notifyAudioDisconnect },
	{ "getNativeVersionInfo", "()Ljava/lang/String;",
		(void *)JNI::getNativeVersionInfo }
};

JNI::JNI() {
}

JNI::~JNI() {
}

jint JNI::onLoad(JavaVM *vm) {
	if (pthread_key_create(&_env_tls, NULL)) {
		return JNI_ERR;
	}

	_vm = vm;

	JNIEnv *env;

	if (_vm->GetEnv((void **)&env, JNI_VERSION_1_2))
		return JNI_ERR;

	if (pthread_setspecific(_env_tls, env)) {
		return JNI_ERR;
	}

	jclass cls = env->FindClass("org/scummvm/scummvm/ScummVM");
	if (cls == 0)
		return JNI_ERR;

	if (env->RegisterNatives(cls, _natives, ARRAYSIZE(_natives)) < 0)
		return JNI_ERR;

	env->DeleteLocalRef(cls);
	return JNI_VERSION_1_2;
}

JNIEnv *JNI::fetchEnv() {
	JNIEnv *env;

	jint res = _vm->GetEnv((void **)&env, JNI_VERSION_1_2);

	if (res != JNI_OK) {
		LOGE("GetEnv() failed: %d", res);
		abort();
	}

	pthread_setspecific(_env_tls, env);

	return env;
}

void JNI::attachThread() {
	JNIEnv *env = 0;

	jint res = _vm->AttachCurrentThread(&env, 0);

	if (res != JNI_OK) {
		LOGE("AttachCurrentThread() failed: %d", res);
		abort();
	}

	if (pthread_setspecific(_env_tls, env)) {
		LOGE("pthread_setspecific() failed");
		abort();
	}
}

void JNI::detachThread() {
	pthread_setspecific(_env_tls, NULL);

	jint res = _vm->DetachCurrentThread();

	if (res != JNI_OK) {
		LOGE("DetachCurrentThread() failed: %d", res);
		abort();
	}
}

void JNI::setReadyForEvents(bool ready) {
	_ready_for_events = ready;
}

void JNI::wakeupForQuit() {
	if (!_system)
		return;

	if (pause) {
		pause = false;

		// wake up all threads except the main one as we are run from it
		for (uint i = 0; i < 2; ++i)
			sem_post(&pause_sem);
	}
}

void JNI::throwByName(JNIEnv *env, const char *name, const char *msg) {
	jclass cls = env->FindClass(name);

	// if cls is 0, an exception has already been thrown
	if (cls != 0)
		env->ThrowNew(cls, msg);

	env->DeleteLocalRef(cls);
}

void JNI::throwRuntimeException(JNIEnv *env, const char *msg) {
	throwByName(env, "java/lang/RuntimeException", msg);
}

// calls to the dark side

void JNI::getDPI(DPIValues &values) {
	// Use sane defaults in case something goes wrong
	values[0] = 160.0;
	values[1] = 160.0;
	values[2] = 1.0;

	JNIEnv *env = JNI::getEnv();

	jfloatArray array = env->NewFloatArray(3);

	env->CallVoidMethod(_jobj, _MID_getDPI, array);

	if (env->ExceptionCheck()) {
		LOGE("Failed to get DPIs");

		env->ExceptionDescribe();
		env->ExceptionClear();
	} else {
		env->GetFloatArrayRegion(array, 0, 3, values);
		if (env->ExceptionCheck()) {
			LOGE("Failed to get DPIs");

			env->ExceptionDescribe();
			env->ExceptionClear();
		}
	}
	LOGD("JNI::getDPI() xdpi: %f, ydpi: %f, density: %f", values[0], values[1], values[2]);
	env->DeleteLocalRef(array);
}

void JNI::displayMessageOnOSD(const Common::U32String &msg) {
	// called from common/osd_message_queue, method: OSDMessageQueue::pollEvent()
	JNIEnv *env = JNI::getEnv();

	jstring java_msg = convertToJString(env, msg);
	if (java_msg == nullptr) {
		// Show a placeholder indicative of the translation error instead of silent failing
		java_msg = env->NewStringUTF("?");
		LOGE("Failed to convert message to UTF-8 for OSD!");
	}

	env->CallVoidMethod(_jobj, _MID_displayMessageOnOSD, java_msg);

	if (env->ExceptionCheck()) {
		LOGE("Failed to display OSD message");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}

	env->DeleteLocalRef(java_msg);
}

bool JNI::openUrl(const Common::String &url) {
	bool success = true;
	JNIEnv *env = JNI::getEnv();
	jstring javaUrl = env->NewStringUTF(url.c_str());

	env->CallVoidMethod(_jobj, _MID_openUrl, javaUrl);

	if (env->ExceptionCheck()) {
		LOGE("Failed to open URL");

		env->ExceptionDescribe();
		env->ExceptionClear();
		success = false;
	}

	env->DeleteLocalRef(javaUrl);
	return success;
}

bool JNI::hasTextInClipboard() {
	JNIEnv *env = JNI::getEnv();
	bool hasText = env->CallBooleanMethod(_jobj, _MID_hasTextInClipboard);

	if (env->ExceptionCheck()) {
		LOGE("Failed to check the contents of the clipboard");

		env->ExceptionDescribe();
		env->ExceptionClear();
		hasText = true;
	}

	return hasText;
}

Common::U32String JNI::getTextFromClipboard() {
	JNIEnv *env = JNI::getEnv();

	jstring javaText = (jstring)env->CallObjectMethod(_jobj, _MID_getTextFromClipboard);

	if (env->ExceptionCheck()) {
		LOGE("Failed to retrieve text from the clipboard");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return Common::U32String();
	}

	Common::U32String text = convertFromJString(env, javaText);
	env->DeleteLocalRef(javaText);

	return text;
}

bool JNI::setTextInClipboard(const Common::U32String &text) {
	JNIEnv *env = JNI::getEnv();
	jstring javaText = convertToJString(env, text);

	bool success = env->CallBooleanMethod(_jobj, _MID_setTextInClipboard, javaText);

	if (env->ExceptionCheck()) {
		LOGE("Failed to add text to the clipboard");

		env->ExceptionDescribe();
		env->ExceptionClear();
		success = false;
	}

	env->DeleteLocalRef(javaText);
	return success;
}

bool JNI::isConnectionLimited() {
	JNIEnv *env = JNI::getEnv();
	bool limited = env->CallBooleanMethod(_jobj, _MID_isConnectionLimited);

	if (env->ExceptionCheck()) {
		LOGE("Failed to check whether connection's limited");

		env->ExceptionDescribe();
		env->ExceptionClear();
		limited = true;
	}

	return limited;
}

void JNI::setWindowCaption(const Common::U32String &caption) {
	JNIEnv *env = JNI::getEnv();
	jstring java_caption = convertToJString(env, caption);

	env->CallVoidMethod(_jobj, _MID_setWindowCaption, java_caption);

	if (env->ExceptionCheck()) {
		LOGE("Failed to set window caption");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}

	env->DeleteLocalRef(java_caption);
}

void JNI::showVirtualKeyboard(bool enable) {
	JNIEnv *env = JNI::getEnv();

	env->CallVoidMethod(_jobj, _MID_showVirtualKeyboard, enable);

	if (env->ExceptionCheck()) {
		LOGE("Error trying to show virtual keyboard");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

void JNI::showOnScreenControls(int enableMask) {
	JNIEnv *env = JNI::getEnv();

	env->CallVoidMethod(_jobj, _MID_showOnScreenControls, enableMask);

	if (env->ExceptionCheck()) {
		LOGE("Error trying to show on screen controls");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

void JNI::setTouchMode(int touchMode) {
	JNIEnv *env = JNI::getEnv();

	env->CallVoidMethod(_jobj, _MID_setTouchMode, touchMode);

	if (env->ExceptionCheck()) {
		LOGE("Error trying to set touch controls mode");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

int JNI::getTouchMode() {
	JNIEnv *env = JNI::getEnv();

	int mode = env->CallIntMethod(_jobj, _MID_getTouchMode);

	if (env->ExceptionCheck()) {
		LOGE("Error trying to get touch controls status");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}

	return mode;
}

void JNI::setOrientation(int orientation) {
	JNIEnv *env = JNI::getEnv();

	env->CallVoidMethod(_jobj, _MID_setOrientation, orientation);

	if (env->ExceptionCheck()) {
		LOGE("Error trying to set orientation");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

Common::String JNI::getScummVMBasePath() {
	JNIEnv *env = JNI::getEnv();

	jstring pathObj = (jstring)env->CallObjectMethod(_jobj, _MID_getScummVMBasePath);

	if (env->ExceptionCheck()) {
		LOGE("Failed to get ScummVM base folder path");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return Common::String();
	}

	Common::String path;
	const char *pathP = env->GetStringUTFChars(pathObj, 0);
	if (pathP != 0) {
		path = Common::String(pathP);
		env->ReleaseStringUTFChars(pathObj, pathP);
	}
	env->DeleteLocalRef(pathObj);

	return path;
}

Common::String JNI::getScummVMAssetsPath() {
	Common::String basePath = getScummVMBasePath();
	if (!basePath.empty() && basePath.lastChar() != '/') {
		basePath += '/';
	}
	basePath += "assets";
	return basePath;
}

Common::String JNI::getScummVMConfigPath() {
	JNIEnv *env = JNI::getEnv();

	jstring pathObj = (jstring)env->CallObjectMethod(_jobj, _MID_getScummVMConfigPath);

	if (env->ExceptionCheck()) {
		LOGE("Failed to get ScummVM config file path");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return Common::String();
	}

	Common::String path;
	const char *pathP = env->GetStringUTFChars(pathObj, 0);
	if (pathP != 0) {
		path = Common::String(pathP);
		env->ReleaseStringUTFChars(pathObj, pathP);
	}
	env->DeleteLocalRef(pathObj);

	return path;
}

Common::String JNI::getScummVMLogPath() {
	JNIEnv *env = JNI::getEnv();

	jstring pathObj = (jstring)env->CallObjectMethod(_jobj, _MID_getScummVMLogPath);

	if (env->ExceptionCheck()) {
		LOGE("Failed to get ScummVM log file path");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return Common::String();
	}

	Common::String path;
	const char *pathP = env->GetStringUTFChars(pathObj, 0);
	if (pathP != 0) {
		path = Common::String(pathP);
		env->ReleaseStringUTFChars(pathObj, pathP);
	}
	env->DeleteLocalRef(pathObj);

	return path;
}

void JNI::setCurrentGame(const Common::String &target) {
	JNIEnv *env = JNI::getEnv();
	jstring java_target = nullptr;
	if (!target.empty()) {
		java_target = convertToJString(env, Common::U32String(target));
	}

	env->CallVoidMethod(_jobj, _MID_setCurrentGame, java_target);

	if (env->ExceptionCheck()) {
		LOGE("Failed to set current game");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}

	if (java_target) {
		env->DeleteLocalRef(java_target);
	}
}

void JNI::reportAdventurePadGameViewport(int left, int top, int right, int bottom,
		int sourceWidth, int sourceHeight) {
	JNIEnv *env = JNI::getEnv();
	env->CallVoidMethod(_jobj, _MID_setAdventurePadGameViewport,
		left, top, right, bottom, sourceWidth, sourceHeight);
	if (env->ExceptionCheck()) {
		LOGE("Failed to report AdventurePad game viewport");
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

bool JNI::isAdventurePadAdvancedLaunch() {
	JNIEnv *env = JNI::getEnv();
	jboolean advancedLaunch = env->CallBooleanMethod(_jobj, _MID_isAdventurePadAdvancedLaunch);

	if (env->ExceptionCheck()) {
		LOGE("Failed to query AdventurePad advanced launch state");
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}

	return advancedLaunch == JNI_TRUE;
}

void JNI::returnToAdventurePad() {
	JNIEnv *env = JNI::getEnv();
	env->CallVoidMethod(_jobj, _MID_returnToAdventurePad);

	if (env->ExceptionCheck()) {
		LOGE("Failed to return to AdventurePad");
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

namespace Android {

bool isAdventurePadAdvancedLaunch() {
	return JNI::isAdventurePadAdvancedLaunch();
}

void returnToAdventurePad() {
	JNI::returnToAdventurePad();
}

} // namespace Android

void JNI::notifyHTTPService(int localPort, bool minimal) {
	JNIEnv *env = JNI::getEnv();

	env->CallVoidMethod(_jobj, _MID_notifyHTTPService, localPort, minimal);

	if (env->ExceptionCheck()) {
		LOGE("Error notifying HTTP service state");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

// The following adds assets folder to search set.
// However searching and retrieving from "assets" on Android this is slow
// so we also make sure to add the base directory, with a higher priority
// This is done via a call to ScummVMActivity's (java) getSysArchives
void JNI::addSysArchivesToSearchSet(Common::SearchSet &s, int priority) {
	JNIEnv *env = JNI::getEnv();

	// get any additional specified paths (from ScummVMActivity code)
	// Insert them with "priority" priority.
	jobjectArray array =
		(jobjectArray)env->CallObjectMethod(_jobj, _MID_getSysArchives);

	if (env->ExceptionCheck()) {
		LOGE("Error finding system archive path");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return;
	}

	jsize size = env->GetArrayLength(array);
	for (jsize i = 0; i < size; ++i) {
		jstring path_obj = (jstring)env->GetObjectArrayElement(array, i);
		const char *path = env->GetStringUTFChars(path_obj, 0);

		if (path != 0) {
			s.addDirectory(path, path, priority, 2);
			env->ReleaseStringUTFChars(path_obj, path);
		}

		env->DeleteLocalRef(path_obj);
	}
	env->DeleteLocalRef(array);

	// add the internal asset (android's structure) with a lower priority,
	// since:
	// 1. It is very slow in accessing large files (eg our growing fonts.dat)
	// 2. we extract the asset contents anyway to the internal app path
	// 3. we pass the internal app path in the process above (via _MID_getSysArchives)
	// However, we keep android APK's "assets" as a fall back, in case something went wrong with the extraction process
	//          and since we had the code anyway
	s.add("ASSET", _asset_archive, priority - 1, false);
}

bool JNI::initSurface() {
	JNIEnv *env = JNI::getEnv();

	jobject obj = env->CallObjectMethod(_jobj, _MID_initSurface);

	if (!obj || env->ExceptionCheck()) {
		LOGE("initSurface failed");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return false;
	}

	_jobj_egl_surface = env->NewGlobalRef(obj);
	env->DeleteLocalRef(obj);

	return true;
}

void JNI::deinitSurface() {
	JNIEnv *env = JNI::getEnv();

	env->DeleteGlobalRef(_jobj_egl_surface);
	_jobj_egl_surface = 0;

	env->CallVoidMethod(_jobj, _MID_deinitSurface);

	if (env->ExceptionCheck()) {
		LOGE("deinitSurface failed");

		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

void JNI::updateMirrorSurface() {
	JNIEnv *env = JNI::getEnv();
	jlongArray update = (jlongArray)env->CallObjectMethod(_jobj, _MID_updateMirrorSurface);
	if (env->ExceptionCheck()) {
		LOGE("updateMirrorSurface failed");
		env->ExceptionDescribe();
		env->ExceptionClear();
		return;
	}
	if (!update)
		return;

	jlong values[4] = { 0, 0, 0, 0 };
	if (env->GetArrayLength(update) == ARRAYSIZE(values))
		env->GetLongArrayRegion(update, 0, ARRAYSIZE(values), values);
	env->DeleteLocalRef(update);

	const int64 previousGeneration = _mirror_surface_generation;
	_have_mirror_surface = values[0] == 1;
	_mirror_surface_generation = values[1];
	_mirror_surface_width = _have_mirror_surface ? values[2] : 0;
	_mirror_surface_height = _have_mirror_surface ? values[3] : 0;
	if (_have_mirror_surface && previousGeneration != _mirror_surface_generation)
		_mirror_diagnostic_frames_remaining = 8;
}

bool JNI::swapBuffers() {
	JNIEnv *env = JNI::getEnv();
	struct timespec started;
	struct timespec finished;
	clock_gettime(CLOCK_MONOTONIC, &started);
	const bool result = env->CallBooleanMethod(_jobj_egl, _MID_EGL10_eglSwapBuffers,
			_jobj_egl_display, _jobj_egl_surface);
	clock_gettime(CLOCK_MONOTONIC, &finished);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	if (_mirror_diagnostic_frames_remaining > 0) {
		const int error = env->CallIntMethod(_jobj_egl, _MID_EGL10_eglGetError);
		const int64 durationMicros = (finished.tv_sec - started.tv_sec) * 1000000LL +
				(finished.tv_nsec - started.tv_nsec) / 1000LL;
		LOGI("AdventurePadMirror primary swap result=%d durationUs=%lld eglError=0x%x surface=%p",
				result, (long long)durationMicros, error, _jobj_egl_surface);
		--_mirror_diagnostic_frames_remaining;
		return result && error == 0x3000;
	}
	return result;
}

bool JNI::makeMirrorSurfaceCurrent() {
	JNIEnv *env = JNI::getEnv();
	const bool result = env->CallBooleanMethod(_jobj, _MID_makeMirrorSurfaceCurrent);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	return result;
}

bool JNI::makePrimarySurfaceCurrent() {
	JNIEnv *env = JNI::getEnv();
	const bool result = env->CallBooleanMethod(_jobj, _MID_makePrimarySurfaceCurrent);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	return result;
}

bool JNI::swapMirrorSurface() {
	JNIEnv *env = JNI::getEnv();
	const bool result = env->CallBooleanMethod(_jobj, _MID_swapMirrorSurface);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	return result;
}

void JNI::reportMirrorStatus(int status, const char *diagnostic) {
	JNIEnv *env = JNI::getEnv();
	jstring javaDiagnostic = env->NewStringUTF(diagnostic);
	env->CallVoidMethod(_jobj, _MID_reportMirrorStatus, status,
			(jlong)_mirror_surface_generation, javaDiagnostic);
	env->DeleteLocalRef(javaDiagnostic);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

void JNI::reportMirrorCursor(int x, int y, bool visible, int64 geometryGeneration) {
	JNIEnv *env = JNI::getEnv();
	env->CallVoidMethod(_jobj, _MID_reportMirrorCursor, x, y,
			visible ? JNI_TRUE : JNI_FALSE, (jlong)geometryGeneration);
	if (env->ExceptionCheck()) {
		LOGE("reportMirrorCursor failed");
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

bool JNI::failMirrorSurface(const char *diagnostic) {
	JNIEnv *env = JNI::getEnv();
	jstring javaDiagnostic = env->NewStringUTF(diagnostic);
	const bool primaryRestored = env->CallBooleanMethod(_jobj, _MID_failMirrorSurface,
			(jlong)_mirror_surface_generation, javaDiagnostic);
	env->DeleteLocalRef(javaDiagnostic);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	_have_mirror_surface = false;
	_mirror_surface_width = 0;
	_mirror_surface_height = 0;
	return primaryRestored;
}

int64 JNI::reportMirrorSourceGeometry(int width, int height, int capability, int orientation) {
	JNIEnv *env = JNI::getEnv();
	const int64 generation = env->CallLongMethod(_jobj, _MID_reportMirrorSourceGeometry,
			width, height, capability, orientation);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return 0;
	}
	return generation;
}

int64 JNI::mirrorGeometryGeneration() {
	JNIEnv *env = JNI::getEnv();
	const int64 generation = env->CallLongMethod(_jobj, _MID_mirrorGeometryGeneration);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return 0;
	}
	return generation;
}

bool JNI::updateMirrorCrop(int64 &cropGeneration, int64 &geometryGeneration,
		float &left, float &top, float &right, float &bottom) {
	JNIEnv *env = JNI::getEnv();
	jdoubleArray update = (jdoubleArray)env->CallObjectMethod(_jobj, _MID_updateMirrorCrop);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	if (!update)
		return false;
	jdouble values[6] = { 0, 0, 0, 0, 1, 1 };
	const bool validLength = env->GetArrayLength(update) == ARRAYSIZE(values);
	if (validLength)
		env->GetDoubleArrayRegion(update, 0, ARRAYSIZE(values), values);
	env->DeleteLocalRef(update);
	if (!validLength)
		return false;
	cropGeneration = (int64)values[0];
	geometryGeneration = (int64)values[1];
	left = (float)values[2];
	top = (float)values[3];
	right = (float)values[4];
	bottom = (float)values[5];
	return true;
}

void JNI::reportMirrorCropAck(int result, int64 cropGeneration,
		int64 geometryGeneration, const char *diagnostic) {
	JNIEnv *env = JNI::getEnv();
	jstring javaDiagnostic = env->NewStringUTF(diagnostic);
	env->CallVoidMethod(_jobj, _MID_reportMirrorCropAck, result,
			(jlong)cropGeneration, (jlong)geometryGeneration, javaDiagnostic);
	env->DeleteLocalRef(javaDiagnostic);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

bool JNI::updateUpperPresentation(int &mode, int64 &modeGeneration,
		int64 &geometryGeneration, float &left, float &top, float &right, float &bottom) {
	JNIEnv *env = JNI::getEnv();
	jdoubleArray update = (jdoubleArray)env->CallObjectMethod(_jobj, _MID_updateUpperPresentation);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
		return false;
	}
	if (!update)
		return false;
	jdouble values[7] = { 0, 0, 0, 0, 0, 1, 1 };
	const bool validLength = env->GetArrayLength(update) == ARRAYSIZE(values);
	if (validLength)
		env->GetDoubleArrayRegion(update, 0, ARRAYSIZE(values), values);
	env->DeleteLocalRef(update);
	if (!validLength)
		return false;
	mode = (int)values[0];
	modeGeneration = (int64)values[1];
	geometryGeneration = (int64)values[2];
	left = (float)values[3];
	top = (float)values[4];
	right = (float)values[5];
	bottom = (float)values[6];
	return true;
}

void JNI::reportUpperPresentationAck(int result, int64 modeGeneration,
		int64 geometryGeneration, const char *diagnostic) {
	JNIEnv *env = JNI::getEnv();
	jstring javaDiagnostic = env->NewStringUTF(diagnostic);
	env->CallVoidMethod(_jobj, _MID_reportUpperPresentationAck, result,
			(jlong)modeGeneration, (jlong)geometryGeneration, javaDiagnostic);
	env->DeleteLocalRef(javaDiagnostic);
	if (env->ExceptionCheck()) {
		env->ExceptionDescribe();
		env->ExceptionClear();
	}
}

int JNI::fetchEGLVersion() {
	JNIEnv *env = JNI::getEnv();

	_egl_version = env->CallIntMethod(_jobj, _MID_eglVersion);

	if (env->ExceptionCheck()) {
		LOGE("eglVersion failed");

		env->ExceptionDescribe();
		env->ExceptionClear();

		_egl_version = 0;
	}

	return _egl_version;
}

// natives for the dark side

void JNI::create(JNIEnv *env, jobject self, jobject asset_manager,
				jobject egl, jobject egl_display,
				jboolean assets_updated_) {
	LOGI("Native version: %s", gScummVMFullVersion);

	assert(!_system);

	// Resolve every JNI method before anything else in case we need it

	// weak global ref to allow class to be unloaded
	// ... except dalvik implements NewWeakGlobalRef only on froyo
	//_jobj = env->NewWeakGlobalRef(self);

	_jobj = env->NewGlobalRef(self);

	jclass cls = env->GetObjectClass(_jobj);

#define FIND_METHOD(prefix, name, signature) do {                           \
    _MID_ ## prefix ## name = env->GetMethodID(cls, #name, signature);      \
        if (_MID_ ## prefix ## name == 0) {                                 \
            LOGE("Can't find function %s", #name);                          \
            abort();                                                        \
        }                                                                   \
    } while (0)

	FIND_METHOD(, getDPI, "([F)V");
	FIND_METHOD(, displayMessageOnOSD, "(Ljava/lang/String;)V");
	FIND_METHOD(, openUrl, "(Ljava/lang/String;)V");
	FIND_METHOD(, hasTextInClipboard, "()Z");
	FIND_METHOD(, getTextFromClipboard, "()Ljava/lang/String;");
	FIND_METHOD(, setTextInClipboard, "(Ljava/lang/String;)Z");
	FIND_METHOD(, isConnectionLimited, "()Z");
	FIND_METHOD(, setWindowCaption, "(Ljava/lang/String;)V");
	FIND_METHOD(, showVirtualKeyboard, "(Z)V");
	FIND_METHOD(, showOnScreenControls, "(I)V");
	FIND_METHOD(, setTouchMode, "(I)V");
	FIND_METHOD(, getTouchMode, "()I");
	FIND_METHOD(, setOrientation, "(I)V");
	FIND_METHOD(, getScummVMBasePath, "()Ljava/lang/String;");
	FIND_METHOD(, getScummVMConfigPath, "()Ljava/lang/String;");
	FIND_METHOD(, getScummVMLogPath, "()Ljava/lang/String;");
	FIND_METHOD(, setCurrentGame, "(Ljava/lang/String;)V");
	FIND_METHOD(, setAdventurePadGameViewport, "(IIIIII)V");
	FIND_METHOD(, isAdventurePadAdvancedLaunch, "()Z");
	FIND_METHOD(, returnToAdventurePad, "()V");
	FIND_METHOD(, notifyHTTPService, "(IZ)V");
	FIND_METHOD(, getSysArchives, "()[Ljava/lang/String;");
	FIND_METHOD(, getAllStorageLocations, "()[Ljava/lang/String;");
	FIND_METHOD(, initSurface, "()Ljavax/microedition/khronos/egl/EGLSurface;");
	FIND_METHOD(, deinitSurface, "()V");
	FIND_METHOD(, updateMirrorSurface, "()[J");
	FIND_METHOD(, makeMirrorSurfaceCurrent, "()Z");
	FIND_METHOD(, makePrimarySurfaceCurrent, "()Z");
	FIND_METHOD(, swapMirrorSurface, "()Z");
	FIND_METHOD(, reportMirrorStatus, "(IJLjava/lang/String;)V");
	FIND_METHOD(, reportMirrorCursor, "(IIZJ)V");
	FIND_METHOD(, failMirrorSurface, "(JLjava/lang/String;)Z");
	FIND_METHOD(, reportMirrorSourceGeometry, "(IIII)J");
	FIND_METHOD(, mirrorGeometryGeneration, "()J");
	FIND_METHOD(, updateMirrorCrop, "()[D");
	FIND_METHOD(, reportMirrorCropAck, "(IJJLjava/lang/String;)V");
	FIND_METHOD(, updateUpperPresentation, "()[D");
	FIND_METHOD(, reportUpperPresentationAck, "(IJJLjava/lang/String;)V");
	FIND_METHOD(, eglVersion, "()I");
	FIND_METHOD(, getNewSAFTree,
	            "(ZLjava/lang/String;Ljava/lang/String;)Lorg/scummvm/scummvm/SAFFSTree;");
	FIND_METHOD(, getSAFTrees, "()[Lorg/scummvm/scummvm/SAFFSTree;");
	FIND_METHOD(, findSAFTree, "(Ljava/lang/String;)Lorg/scummvm/scummvm/SAFFSTree;");
	FIND_METHOD(, exportBackup, "(Ljava/lang/String;)I");
	FIND_METHOD(, importBackup, "(Ljava/lang/String;Ljava/lang/String;)I");

	_jobj_egl = env->NewGlobalRef(egl);
	_jobj_egl_display = env->NewGlobalRef(egl_display);
	_egl_version = 0;

	env->DeleteLocalRef(cls);

	cls = env->GetObjectClass(_jobj_egl);

	FIND_METHOD(EGL10_, eglSwapBuffers,
				"(Ljavax/microedition/khronos/egl/EGLDisplay;"
				"Ljavax/microedition/khronos/egl/EGLSurface;)Z");
	FIND_METHOD(EGL10_, eglGetError, "()I");

	env->DeleteLocalRef(cls);
#undef FIND_METHOD

	assets_updated = assets_updated_;

	// Initialize network bindings here in a Java thread
	// If net called gets called for the first time in a timer thread,
	// the class loader is lost and can't find our classes.
	Networking::NetJNI::init(env);

	pause = false;
	// initial value of zero!
	sem_init(&pause_sem, 0, 0);

	virt_keyboard_state = false;

	_asset_archive = new AndroidAssetArchive(asset_manager);
	assert(_asset_archive);

	_system = new OSystem_Android();
	assert(_system);

	g_system = _system;
}

void JNI::destroy(JNIEnv *env, jobject self) {
	_have_mirror_surface = false;
	_mirror_surface_width = 0;
	_mirror_surface_height = 0;
	_mirror_surface_generation = 0;
	_mirror_diagnostic_frames_remaining = 0;

	delete _asset_archive;
	_asset_archive = 0;

	// _system is a pointer of OSystem_Android <--- ModularBackend <--- BaseBacked <--- Common::OSystem
	// It's better to call destroy() rather than just delete here
	// to avoid mutex issues if a Common::String is used after this point
	_system->destroy();

	g_system = 0;
	_system  = 0;

	sem_destroy(&pause_sem);

	// see above
	//JNI::getEnv()->DeleteWeakGlobalRef(_jobj);

	JNI::getEnv()->DeleteGlobalRef(_jobj_egl_display);
	JNI::getEnv()->DeleteGlobalRef(_jobj_egl);
	JNI::getEnv()->DeleteGlobalRef(_jobj);
}

void JNI::setSurface(JNIEnv *env, jobject self, jint width, jint height, jint bpp) {
	egl_surface_width = width;
	egl_surface_height = height;
	egl_bits_per_pixel = bpp;
	surface_changeid++;
}

jint JNI::main(JNIEnv *env, jobject self, jobjectArray args) {
	assert(_system);

	const int MAX_NARGS = 32;
	int res = -1;

	int argc = env->GetArrayLength(args);
	if (argc > MAX_NARGS) {
		throwByName(env, "java/lang/IllegalArgumentException",
					"too many arguments");
		return 0;
	}

	char *argv[MAX_NARGS];

	// note use in cleanup loop below
	int nargs;

	for (nargs = 0; nargs < argc; ++nargs) {
		jstring arg = (jstring)env->GetObjectArrayElement(args, nargs);

		if (arg == 0) {
			argv[nargs] = 0;
		} else {
			const char *cstr = env->GetStringUTFChars(arg, 0);

			argv[nargs] = const_cast<char *>(cstr);

			// exception already thrown?
			if (cstr == 0)
				goto cleanup;
		}

		env->DeleteLocalRef(arg);
	}

	LOGI("Entering scummvm_main with %d args", argc);

	res = scummvm_main(argc, argv);

	LOGI("scummvm_main exited with code %d", res);

	_system->quit();

cleanup:
	nargs--;

	for (int i = 0; i < nargs; ++i) {
		if (argv[i] == 0)
			continue;

		jstring arg = (jstring)env->GetObjectArrayElement(args, nargs);

		// Exception already thrown?
		if (arg == 0)
			return res;

		env->ReleaseStringUTFChars(arg, argv[i]);
		env->DeleteLocalRef(arg);
	}

	return res;
}

void JNI::pushEvent(JNIEnv *env, jobject self, int type, int arg1, int arg2,
					int arg3, int arg4, int arg5, int arg6) {
	// drop events until we're ready and after we quit
	if (!_ready_for_events) {
		LOGW("dropping event");
		return;
	}

	assert(_system);

	_system->pushEvent(type, arg1, arg2, arg3, arg4, arg5, arg6);
}

void JNI::updateTouch(JNIEnv *env, jobject self, int action, int ptr, int x, int y) {
	// drop events until we're ready and after we quit
	if (!_ready_for_events) {
		LOGW("dropping event");
		return;
	}

	assert(_system);

	_system->getTouchControls().update((TouchControls::Action) action, ptr, x, y);
}

void JNI::setupTouchMode(JNIEnv *env, jobject self, jint oldValue, jint newValue) {
	if (!_system)
		return;

	_system->setupTouchMode(oldValue, newValue);
}

void JNI::syncVirtkeyboardState(JNIEnv *env, jobject self, jboolean newState) {
	if (!_system)
		return;

	JNI::virt_keyboard_state = newState;
}

void JNI::setPause(JNIEnv *env, jobject self, jboolean value) {
	if (!_system)
		return;

	_system->setPause(value);

	if (pause != value) {
		pause = value;

		if (!pause) {
			// wake up all threads
			for (uint i = 0; i < 3; ++i)
				sem_post(&pause_sem);
		}
	}
}

void JNI::systemInsetsUpdated(JNIEnv *env, jobject self, jintArray gestureInsets, jintArray systemInsets, jintArray cutoutInsets) {
	assert(env->GetArrayLength(gestureInsets) == ARRAYSIZE(gestures_insets));

	env->GetIntArrayRegion(gestureInsets, 0, ARRAYSIZE(gestures_insets), gestures_insets);
	env->GetIntArrayRegion(cutoutInsets, 0, ARRAYSIZE(cutout_insets), cutout_insets);
}

// JNI::setDefaultAudioValues and JNI::notifyAudioDisconnect are in android-mixer.cpp

jstring JNI::getNativeVersionInfo(JNIEnv *env, jobject self) {
	return convertToJString(env, Common::U32String(gScummVMVersion));
}

jint JNI::getAndroidSDKVersionId() {
	// based on: https://stackoverflow.com/a/10511880
	JNIEnv *env = JNI::getEnv();
	// VERSION is a nested class within android.os.Build (hence "$" rather than "/")
	jclass versionClass = env->FindClass("android/os/Build$VERSION");
	if (!versionClass) {
		return 0;
	}

	jfieldID sdkIntFieldID = NULL;
	sdkIntFieldID = env->GetStaticFieldID(versionClass, "SDK_INT", "I");
	if (!sdkIntFieldID) {
		return 0;
	}

	jint sdkInt = env->GetStaticIntField(versionClass, sdkIntFieldID);
	//LOGD("sdkInt = %d", sdkInt);

	env->DeleteLocalRef(versionClass);
	return sdkInt;
}

jstring JNI::convertToJString(JNIEnv *env, const Common::U32String &str) {
	uint len = 0;
	uint16 *u16str = str.encodeUTF16Native(&len);
	jstring jstr = env->NewString(u16str, len);
	delete[] u16str;
	return jstr;
}

Common::U32String JNI::convertFromJString(JNIEnv *env, const jstring &jstr) {
	const uint16 *utf16Str = env->GetStringChars(jstr, 0);
	uint jcount = env->GetStringLength(jstr);
	if (!utf16Str)
		return Common::U32String();
	Common::U32String str = Common::U32String::decodeUTF16Native(utf16Str, jcount);
	env->ReleaseStringChars(jstr, utf16Str);

	return str;
}

// TODO should this be a U32String array?
Common::Array<Common::String> JNI::getAllStorageLocations() {
	Common::Array<Common::String> res;

	JNIEnv *env = JNI::getEnv();

	jobjectArray array =
		(jobjectArray)env->CallObjectMethod(_jobj, _MID_getAllStorageLocations);

	if (env->ExceptionCheck()) {
		LOGE("Error finding system archive path");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return res;
	}

	jsize size = env->GetArrayLength(array);
	for (jsize i = 0; i < size; ++i) {
		jstring path_obj = (jstring)env->GetObjectArrayElement(array, i);
		const char *path = env->GetStringUTFChars(path_obj, 0);

		if (path != 0) {
			res.push_back(path);
			env->ReleaseStringUTFChars(path_obj, path);
		}

		env->DeleteLocalRef(path_obj);
	}

	env->DeleteLocalRef(array);
	return res;
}

jobject JNI::getNewSAFTree(bool writable, const Common::String &initURI,
                           const Common::String &prompt) {
	JNIEnv *env = JNI::getEnv();
	jstring javaInitURI = env->NewStringUTF(initURI.c_str());
	jstring javaPrompt = env->NewStringUTF(prompt.c_str());

	jobject tree = env->CallObjectMethod(_jobj, _MID_getNewSAFTree,
	                                     writable, javaInitURI, javaPrompt);

	env->DeleteLocalRef(javaInitURI);
	env->DeleteLocalRef(javaPrompt);

	if (env->ExceptionCheck()) {
		LOGE("getNewSAFTree: error");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return nullptr;
	}

	return tree;
}

Common::Array<jobject> JNI::getSAFTrees() {
	Common::Array<jobject> res;

	JNIEnv *env = JNI::getEnv();

	jobjectArray array =
	    (jobjectArray)env->CallObjectMethod(_jobj, _MID_getSAFTrees);

	if (env->ExceptionCheck()) {
		LOGE("getSAFTrees: error");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return res;
	}

	jsize size = env->GetArrayLength(array);
	for (jsize i = 0; i < size; ++i) {
		jobject tree = env->GetObjectArrayElement(array, i);
		res.push_back(tree);
	}
	env->DeleteLocalRef(array);

	return res;
}

jobject JNI::findSAFTree(const Common::String &name) {
	JNIEnv *env = JNI::getEnv();

	jstring nameObj = env->NewStringUTF(name.c_str());

	jobject tree = env->CallObjectMethod(_jobj, _MID_findSAFTree, nameObj);

	env->DeleteLocalRef(nameObj);

	if (env->ExceptionCheck()) {
		LOGE("findSAFTree: error");

		env->ExceptionDescribe();
		env->ExceptionClear();

		return nullptr;
	}

	return tree;
}

int JNI::exportBackup(const Common::U32String &prompt) {
	JNIEnv *env = JNI::getEnv();

	jstring promptObj = convertToJString(env, prompt);

	jint result = env->CallIntMethod(_jobj, _MID_exportBackup, promptObj);

	env->DeleteLocalRef(promptObj);

	if (env->ExceptionCheck()) {
		LOGE("exportBackup: error");

		env->ExceptionDescribe();
		env->ExceptionClear();

		// BackupManager.ERROR_INVALID_BACKUP
		return -1;
	}

	return result;
}

int JNI::importBackup(const Common::U32String &prompt, const Common::String &path) {
	JNIEnv *env = JNI::getEnv();

	jstring promptObj = convertToJString(env, prompt);

	jstring pathObj = nullptr;
	if (!path.empty()) {
		pathObj = env->NewStringUTF(path.c_str());
	}

	jint result = env->CallIntMethod(_jobj, _MID_importBackup, promptObj, pathObj);

	env->DeleteLocalRef(pathObj);
	env->DeleteLocalRef(promptObj);

	if (env->ExceptionCheck()) {
		LOGE("importBackup: error");

		env->ExceptionDescribe();
		env->ExceptionClear();

		// BackupManager.ERROR_INVALID_BACKUP
		return -1;
	}

	return result;
}
