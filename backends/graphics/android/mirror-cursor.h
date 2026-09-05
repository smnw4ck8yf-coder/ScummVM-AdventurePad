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

#ifndef BACKENDS_GRAPHICS_ANDROID_MIRROR_CURSOR_H
#define BACKENDS_GRAPHICS_ANDROID_MIRROR_CURSOR_H

#include <cmath>

struct AndroidMirrorCursorPresentation {
	bool upperVisible;
	bool lowerVisible;
	float lowerX;
	float lowerY;
	float lowerWidth;
	float lowerHeight;
};

inline bool deriveAndroidMirrorCursorPresentation(float hotspotX, float hotspotY,
		float cursorLeft, float cursorTop, float cursorWidth, float cursorHeight,
		float splitY, float cropLeft, float cropTop, float cropRight, float cropBottom,
		float destinationX, float destinationY, float destinationWidth, float destinationHeight,
		AndroidMirrorCursorPresentation &presentation) {
	const bool valid = std::isfinite(hotspotX) && std::isfinite(hotspotY) &&
		std::isfinite(cursorLeft) && std::isfinite(cursorTop) &&
		std::isfinite(cursorWidth) && std::isfinite(cursorHeight) &&
		std::isfinite(splitY) && std::isfinite(cropLeft) && std::isfinite(cropTop) &&
		std::isfinite(cropRight) && std::isfinite(cropBottom) &&
		std::isfinite(destinationX) && std::isfinite(destinationY) &&
		std::isfinite(destinationWidth) && std::isfinite(destinationHeight) &&
		splitY > 0.0f && splitY < 1.0f && cropLeft >= 0.0f && cropTop >= 0.0f &&
		cropRight <= 1.0f && cropBottom <= 1.0f && cropLeft < cropRight &&
		cropTop < cropBottom && std::fabs(splitY - cropTop) <= 0.0001f &&
		destinationWidth > 0.0f && destinationHeight > 0.0f;
	if (!valid)
		return false;

	presentation.upperVisible = hotspotX >= 0.0f && hotspotX <= 1.0f &&
		hotspotY >= 0.0f && hotspotY < splitY;
	presentation.lowerVisible = hotspotX >= cropLeft && hotspotX <= cropRight &&
		hotspotY >= cropTop && hotspotY < cropBottom;
	const float cropWidth = cropRight - cropLeft;
	const float cropHeight = cropBottom - cropTop;
	presentation.lowerX = destinationX + (cursorLeft - cropLeft) * destinationWidth / cropWidth;
	presentation.lowerY = destinationY + (cursorTop - cropTop) * destinationHeight / cropHeight;
	presentation.lowerWidth = cursorWidth * destinationWidth / cropWidth;
	presentation.lowerHeight = cursorHeight * destinationHeight / cropHeight;
	return true;
}

#endif
