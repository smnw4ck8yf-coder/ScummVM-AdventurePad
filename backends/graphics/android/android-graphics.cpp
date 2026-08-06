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

// Allow use of stuff in <time.h>
#define FORBIDDEN_SYMBOL_EXCEPTION_time_h

// Disable printf override in common/forbidden.h to avoid
// clashes with log.h from the Android SDK.
// That header file uses
//   __attribute__ ((format(printf, 3, 4)))
// which gets messed up by our override mechanism; this could
// be avoided by either changing the Android SDK to use the equally
// legal and valid
//   __attribute__ ((format(printf, 3, 4)))
// or by refining our printf override to use a varadic macro
// (which then wouldn't be portable, though).
// Anyway, for now we just disable the printf override globally
// for the Android port
#define FORBIDDEN_SYMBOL_EXCEPTION_printf

#include "backends/platform/android/android.h"
#include "backends/platform/android/jni-android.h"
#include "backends/graphics/android/android-graphics.h"
#include "backends/graphics/opengl/pipelines/pipeline.h"
#include "backends/graphics/opengl/renderer3d.h"
#include "backends/graphics/opengl/texture.h"

#include "graphics/blit.h"
#include "graphics/managed_surface.h"
#include "graphics/opengl/debug.h"

namespace {

void logMirrorGLState(const char *stage) {
	GLint framebuffer = 0;
	GLint viewport[4] = { 0, 0, 0, 0 };
	GLint scissorBox[4] = { 0, 0, 0, 0 };
	GLint program = 0;
	GLint activeTexture = 0;
	GLint texture = 0;
	GLboolean scissor = GL_FALSE;
	GLboolean blend = GL_FALSE;
	glGetIntegerv(GL_FRAMEBUFFER_BINDING, &framebuffer);
	glGetIntegerv(GL_VIEWPORT, viewport);
	glGetBooleanv(GL_SCISSOR_TEST, &scissor);
	glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
	glGetBooleanv(GL_BLEND, &blend);
	glGetIntegerv(GL_CURRENT_PROGRAM, &program);
	glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTexture);
	glGetIntegerv(GL_TEXTURE_BINDING_2D, &texture);
	const GLenum error = glGetError();
	LOGI("AdventurePadMirror GL %s framebuffer=%d viewport=%d,%d %dx%d scissor=%d box=%d,%d %dx%d blend=%d program=%d activeTexture=0x%x texture=%d glError=0x%x",
			stage, framebuffer, viewport[0], viewport[1], viewport[2], viewport[3],
			scissor, scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3],
			blend, program, activeTexture, texture, error);
}

void logMirrorProjectionState(const char *stage, const OpenGL::Framebuffer &target,
		int width, int height, bool activatedAfterSurfaceSwitch) {
	const float *projection = target.getProjectionMatrix().getData();
	LOGI("AdventurePadMirror target %s size=%dx%d projection=[%.7f %.7f %.7f %.7f | %.7f %.7f %.7f %.7f | %.7f %.7f %.7f %.7f | %.7f %.7f %.7f %.7f] model=none projectionUniformAppliedOnActivate=%d",
			stage, width, height,
			projection[0], projection[1], projection[2], projection[3],
			projection[4], projection[5], projection[6], projection[7],
			projection[8], projection[9], projection[10], projection[11],
			projection[12], projection[13], projection[14], projection[15],
			activatedAfterSurfaceSwitch);
}

} // End of anonymous namespace

//
// AndroidGraphicsManager
//
AndroidGraphicsManager::AndroidGraphicsManager() :
	_touchcontrols(nullptr),
	_mirrorTarget(),
	_mirrorGeneration(0),
	_mirrorSourceState(-1),
	_mirrorDiagnosticFramesRemaining(0),
	_old_touch_mode(OSystem_Android::TOUCH_MODE_TOUCHPAD) {
	ENTER();

	// Initialize our OpenGL ES context.
	initSurface();

	_rendering3d = (_renderer3d != nullptr);
	// maybe in 3D, not in GUI
	dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_rendering3d, false);
	dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
}

AndroidGraphicsManager::~AndroidGraphicsManager() {
	ENTER();

	deinitSurface();

	delete _touchcontrols;
}

void AndroidGraphicsManager::initSurface() {
	LOGD("initializing 2D surface");

	assert(!JNI::haveSurface());
	if (!JNI::initSurface()) {
		error("JNI::initSurface failed");
	}

	if (JNI::egl_bits_per_pixel == 16) {
		// We default to RGB565 and RGBA5551 which is closest to what we setup in Java side
		notifyContextCreate(OpenGL::kContextGLES2,
				new OpenGL::Backbuffer(),
				Graphics::PixelFormat(2, 5, 6, 5, 0, 11, 5, 0, 0),
				Graphics::PixelFormat(2, 5, 5, 5, 1, 11, 6, 1, 0));
	} else {
		// If not 16, this must be 24 or 32 bpp so make use of them
		notifyContextCreate(OpenGL::kContextGLES2,
				new OpenGL::Backbuffer(),
				OpenGL::Texture::getRGBPixelFormat(),
				OpenGL::Texture::getRGBAPixelFormat()
		);
	}

	if (_touchcontrols) {
		_touchcontrols->recreate();
		_touchcontrols->updateGLTexture();
	} else {
		_touchcontrols = createSurface(_defaultFormatAlpha);
	}
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    this, JNI::egl_surface_width, JNI::egl_surface_height);

	handleResize(JNI::egl_surface_width, JNI::egl_surface_height);
}

void AndroidGraphicsManager::deinitSurface() {
	if (!JNI::haveSurface())
		return;

	LOGD("deinitializing 2D surface");

	// Deregister us from touch control
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    nullptr, 0, 0);
	if (_touchcontrols) {
		_touchcontrols->destroy();
	}

	notifyContextDestroy();

	JNI::deinitSurface();
}

void AndroidGraphicsManager::resizeSurface() {

	// If we had lost surface just init it again
	if (!JNI::haveSurface()) {
		initSurface();
		return;
	}

	// Recreate the EGL surface, context is preserved
	JNI::deinitSurface();
	if (!JNI::initSurface()) {
		error("JNI::initSurface failed");
	}

	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().setDrawer(
	    this, JNI::egl_surface_width, JNI::egl_surface_height);

	handleResize(JNI::egl_surface_width, JNI::egl_surface_height);
}


void AndroidGraphicsManager::updateScreen() {
	//ENTER();

	if (!JNI::haveSurface())
		return;

	// Attach and detach requests arrive on Android's main thread, but EGL
	// lifecycle work is consumed here on ScummVM's render thread.
	JNI::updateMirrorSurface();

	// Sets _forceRedraw if needed
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().beforeDraw();

	OpenGLGraphicsManager::updateScreen();
	renderMirrorSurface();
}

void AndroidGraphicsManager::renderMirrorSurface() {
	if (!JNI::haveMirrorSurface()) {
		_mirrorGeneration = 0;
		_mirrorSourceState = -1;
		return;
	}

	const int64 generation = JNI::mirrorSurfaceGeneration();
	if (_mirrorGeneration != generation) {
		_mirrorGeneration = generation;
		_mirrorSourceState = -1;
		_mirrorDiagnosticFramesRemaining = 8;
	}

	const OpenGL::Texture *sourceTexture = nullptr;
	if (_gameScreen) {
		sourceTexture = &_gameScreen->getGLTexture();
	}
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	else if (_renderer3d && _renderer3d->hasTexture()) {
		sourceTexture = &_renderer3d->getGLTexture();
	}
#endif

	if (!sourceTexture || sourceTexture->getLogicalWidth() == 0 || sourceTexture->getLogicalHeight() < 4) {
		if (_mirrorSourceState != 0) {
			JNI::reportMirrorStatus(2, "Current renderer has no reusable game texture");
			_mirrorSourceState = 0;
		}
		return;
	}
	if (_mirrorDiagnosticFramesRemaining > 0) {
		const char *sourceType = _gameScreen ? "gameScreen" : "renderer3d";
		const bool textureValid = glIsTexture(sourceTexture->getGLTexture()) == GL_TRUE;
		const bool uploadComplete = !_gameScreen || !_gameScreen->isDirty();
		const GLenum textureError = glGetError();
		LOGI("AdventurePadMirror source=%s logical=%ux%u allocated=%ux%u id=%u valid=%d dirty=%d gpuDataReady=%d linearFilter=%d glError=0x%x",
				sourceType, sourceTexture->getLogicalWidth(), sourceTexture->getLogicalHeight(),
				sourceTexture->getWidth(), sourceTexture->getHeight(), sourceTexture->getGLTexture(),
				textureValid, _gameScreen ? _gameScreen->isDirty() : 0,
				textureValid && uploadComplete, sourceTexture->isLinearFilteringEnabled(), textureError);
		logMirrorGLState("primary-before-switch");
		if (!textureValid || !uploadComplete || textureError != GL_NO_ERROR) {
			JNI::failMirrorSurface("Reusable source texture is not valid in the primary context");
			return;
		}
	}

	const int surfaceWidth = JNI::mirrorSurfaceWidth();
	const int surfaceHeight = JNI::mirrorSurfaceHeight();
	if (surfaceWidth <= 0 || surfaceHeight <= 0) {
		JNI::failMirrorSurface("Mirror surface dimensions became invalid");
		return;
	}

#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	const bool restore3D = _renderer3d != nullptr;
	if (restore3D)
		_renderer3d->leave3D();
#endif

	OpenGL::Pipeline *pipeline = getPipeline();
	if (!pipeline) {
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
		if (restore3D)
			_renderer3d->enter3D();
#endif
		JNI::failMirrorSurface("OpenGL pipeline is unavailable");
		return;
	}
	GLint primaryViewport[4] = { 0, 0, 0, 0 };
	if (_mirrorDiagnosticFramesRemaining > 0)
		glGetIntegerv(GL_VIEWPORT, primaryViewport);
	OpenGL::Pipeline::disable();
	OpenGL::Framebuffer *primaryTarget = pipeline->setFramebuffer(&_mirrorTarget);
	if (_mirrorDiagnosticFramesRemaining > 0)
		logMirrorProjectionState("primary-before-switch", *primaryTarget,
				primaryViewport[2], primaryViewport[3], false);
	_mirrorTarget.setSize(surfaceWidth, surfaceHeight);

	if (!JNI::makeMirrorSurfaceCurrent()) {
		const bool primaryRestored = JNI::failMirrorSurface("Could not make the mirror EGLSurface current");
		pipeline->setFramebuffer(primaryTarget);
		if (primaryRestored)
			pipeline->activate();
#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
		if (restore3D && primaryRestored)
			_renderer3d->enter3D();
#endif
		return;
	}

	pipeline->activate();
	if (_mirrorDiagnosticFramesRemaining > 0)
		logMirrorProjectionState("mirror-active", _mirrorTarget, surfaceWidth, surfaceHeight, true);
	_mirrorTarget.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	_mirrorTarget.enableScissorTest(false);
	_mirrorTarget.enableBlend(OpenGL::Framebuffer::kBlendModeOpaque);
	pipeline->setColor(1.0f, 1.0f, 1.0f, 1.0f);
	if (_mirrorDiagnosticFramesRemaining > 0)
		logMirrorGLState("mirror-before-clear");
	GL_CALL(glClear(GL_COLOR_BUFFER_BIT));

	const int sourceWidth = sourceTexture->getLogicalWidth();
	const int sourceHeight = sourceTexture->getLogicalHeight();
	int destinationWidth = surfaceWidth;
	int destinationHeight = (int)((int64)surfaceWidth * sourceHeight / sourceWidth);
	if (destinationHeight > surfaceHeight) {
		destinationHeight = surfaceHeight;
		destinationWidth = (int)((int64)surfaceHeight * sourceWidth / sourceHeight);
	}
	const int destinationX = (surfaceWidth - destinationWidth) / 2;
	const int destinationY = (surfaceHeight - destinationHeight) / 2;
	const GLenum beforeDrawError = glGetError();
	// Use the exact full-texture overload used by the successful upper draw.
	// This preserves the texture object's canonical flip/rotation coordinates.
	pipeline->drawTexture(*sourceTexture, destinationX, destinationY,
			destinationWidth, destinationHeight);
	const GLenum drawError = glGetError();
	if (_mirrorDiagnosticFramesRemaining > 0) {
		LOGI("AdventurePadMirror draw source=0,0-%d,%d destinationVertices=[%d,%d %d,%d %d,%d %d,%d] surface=%dx%d glErrorBefore=0x%x glErrorAfter=0x%x",
				sourceWidth, sourceHeight, destinationX, destinationY,
				destinationX + destinationWidth, destinationY,
				destinationX, destinationY + destinationHeight,
				destinationX + destinationWidth, destinationY + destinationHeight,
				surfaceWidth, surfaceHeight,
				beforeDrawError, drawError);
		logMirrorGLState("mirror-after-draw");
	}

	const bool swapped = beforeDrawError == GL_NO_ERROR && drawError == GL_NO_ERROR && JNI::swapMirrorSurface();
	OpenGL::Pipeline::disable();
	bool primaryRestored = JNI::makePrimarySurfaceCurrent();
	if (!primaryRestored) {
		primaryRestored = JNI::failMirrorSurface("Could not restore the primary EGLSurface");
	} else if (!swapped) {
		primaryRestored = JNI::failMirrorSurface(beforeDrawError == GL_NO_ERROR && drawError == GL_NO_ERROR ?
				"Mirror buffer swap failed" : "Mirror draw produced a GL error");
	}
	pipeline->setFramebuffer(primaryTarget);
	if (primaryRestored)
		pipeline->activate();

#if defined(USE_OPENGL_GAME) || defined(USE_OPENGL_SHADERS)
	if (restore3D && primaryRestored)
		_renderer3d->enter3D();
#endif
	if (_mirrorDiagnosticFramesRemaining > 0) {
		if (primaryRestored) {
			logMirrorProjectionState("primary-restored", *primaryTarget,
					primaryViewport[2], primaryViewport[3], true);
			logMirrorGLState("primary-restored");
		}
		LOGI("AdventurePadMirror primary restoration verified=%d", primaryRestored);
		--_mirrorDiagnosticFramesRemaining;
	}

	if (!swapped || !primaryRestored) {
		_mirrorSourceState = -1;
		return;
	}

	if (_mirrorSourceState != 1) {
		JNI::reportMirrorStatus(1, "Live full-frame texture mirror supported");
		_mirrorSourceState = 1;
	}
}

void AndroidGraphicsManager::displayMessageOnOSD(const Common::U32String &msg) {
	ENTER("%s", msg.encode().c_str());

	JNI::displayMessageOnOSD(msg);
}

void AndroidGraphicsManager::recalculateDisplayAreas() {
	Common::Rect oldDrawRect = _activeArea.drawRect;

	OpenGLGraphicsManager::recalculateDisplayAreas();

	int offsetX = _activeArea.drawRect.left - oldDrawRect.left;
	int offsetY = _activeArea.drawRect.top - oldDrawRect.top;

	int newX = _cursorX + offsetX;
	int newY = _cursorY + offsetY;

	newX = CLIP<int16>(newX, _activeArea.drawRect.left, _activeArea.drawRect.right);
	newY = CLIP<int16>(newY, _activeArea.drawRect.top, _activeArea.drawRect.bottom);

	setMousePosition(newX, newY);
}

void AndroidGraphicsManager::showOverlay(bool inGUI) {
	if (_overlayVisible && inGUI == _overlayInGUI)
		return;

	// Don't change touch mode when not changing mouse coordinates
	if (inGUI) {
		_old_touch_mode = JNI::getTouchMode();
		// maybe in 3D, in overlay
		dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_renderer3d != nullptr, true);
		dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
	} else if (_overlayInGUI) {
		// Restore touch mode active before overlay was shown
		JNI::setTouchMode(_old_touch_mode);
	}

	OpenGL::OpenGLGraphicsManager::showOverlay(inGUI);
}

void AndroidGraphicsManager::hideOverlay() {
	if (!_overlayVisible)
		return;

	if (_overlayInGUI) {
		// Restore touch mode active before overlay was shown
		JNI::setTouchMode(_old_touch_mode);
		dynamic_cast<OSystem_Android *>(g_system)->applyOrientationSettings();
	}

	OpenGL::OpenGLGraphicsManager::hideOverlay();
}

float AndroidGraphicsManager::getHiDPIScreenFactor() const {
	JNI::DPIValues dpi;
	JNI::getDPI(dpi);
	// Scale down the Android factor else the GUI is too big and
	// there is not much options to go smaller
	return dpi[2] / 1.2f;
}

bool AndroidGraphicsManager::loadVideoMode(uint requestedWidth, uint requestedHeight, bool resizable, int antialiasing) {
	ENTER("%d, %d, %d, %d", requestedWidth, requestedHeight, resizable, antialiasing);

	// As GLES2 provides FBO, OpenGL graphics manager must ask us for a resizable surface
	assert(resizable);
	if (antialiasing != 0) {
		warning("Requesting antialiased video mode while not available");
	}

	const bool render3d = (_renderer3d != nullptr);
	if (_rendering3d != render3d) {
		_rendering3d = render3d;
		// 3D status changed: refresh the touch mode
		applyTouchSettings();
	}

	// We get this whenever a new resolution is requested. Since Android is
	// using a fixed output size we do nothing like that here.
	return true;
}

void AndroidGraphicsManager::refreshScreen() {
	//ENTER();

	// Last minute draw of touch controls
	dynamic_cast<OSystem_Android *>(g_system)->getTouchControls().draw();

	JNI::swapBuffers();
}

void AndroidGraphicsManager::applyTouchSettings() const {
	// maybe in 3D, maybe in GUI
	dynamic_cast<OSystem_Android *>(g_system)->applyTouchSettings(_renderer3d != nullptr, _overlayVisible && _overlayInGUI);
}

void AndroidGraphicsManager::syncVirtkeyboardState(bool virtkeybd_on) {
	_screenAlign = SCREEN_ALIGN_CENTER;
	if (virtkeybd_on) {
		_screenAlign |= SCREEN_ALIGN_TOP;
	} else {
		_screenAlign |= SCREEN_ALIGN_MIDDLE;
	}
	recalculateDisplayAreas();
	_forceRedraw = true;
}

void AndroidGraphicsManager::touchControlInitSurface(const Graphics::ManagedSurface &surf) {
	if (_touchcontrols->getWidth() == (uint)surf.w && _touchcontrols->getHeight() == (uint)surf.h) {
		return;
	}

	_touchcontrols->allocate(surf.w, surf.h);
	Graphics::Surface *dst = _touchcontrols->getSurface();

	Graphics::crossBlit(
			(byte *)dst->getPixels(), (const byte *)surf.getPixels(),
			dst->pitch, surf.pitch,
			surf.w, surf.h,
			dst->format, surf.format);
	_touchcontrols->updateGLTexture();
}

void AndroidGraphicsManager::touchControlDraw(uint8 alpha, int16 x, int16 y, int16 w, int16 h, const Common::Rect &clip) {
	_targetBuffer->enableBlend(OpenGL::Framebuffer::kBlendModeTraditionalTransparency);
	OpenGL::Pipeline *pipeline = getPipeline();
	pipeline->activate();
	if (alpha != 255) {
		pipeline->setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
	}
	pipeline->drawTexture(_touchcontrols->getGLTexture(),
	                      x, y, w, h, clip);
	if (alpha != 255) {
		pipeline->setColor(1.0f, 1.0f, 1.0f, 1.0f);
	}
}

void AndroidGraphicsManager::touchControlNotifyChanged() {
	// Make sure we redraw the screen
	_forceRedraw = true;
}

bool AndroidGraphicsManager::notifyMousePosition(Common::Point &mouse) {
	mouse.x = CLIP<int16>(mouse.x, _activeArea.drawRect.left, _activeArea.drawRect.right);
	mouse.y = CLIP<int16>(mouse.y, _activeArea.drawRect.top, _activeArea.drawRect.bottom);

	setMousePosition(mouse.x, mouse.y);
	mouse = convertWindowToVirtual(mouse.x, mouse.y);

	return true;
}

WindowedGraphicsManager::Insets AndroidGraphicsManager::getSafeAreaInsets() const {
	return WindowedGraphicsManager::Insets{
		(int16)JNI::cutout_insets[0], (int16)JNI::cutout_insets[1],
		(int16)JNI::cutout_insets[2], (int16)JNI::cutout_insets[3]};
}
