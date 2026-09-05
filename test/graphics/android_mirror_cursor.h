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

#include <cxxtest/TestSuite.h>

#include "backends/graphics/android/mirror-cursor.h"

class AndroidMirrorCursorTestSuite : public CxxTest::TestSuite {
public:
	void testCursorAboveSplitBelongsOnlyToUpperDisplay() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.5f, 0.74f,
			0.48f, 0.72f, 0.04f, 0.08f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			0.0f, 0.0f, 800.0f, 200.0f, result));
		TS_ASSERT(result.upperVisible);
		TS_ASSERT(!result.lowerVisible);
	}

	void testCursorAtSplitBelongsOnlyToLowerDisplay() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.5f, 0.75f,
			0.48f, 0.73f, 0.04f, 0.08f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			0.0f, 0.0f, 800.0f, 200.0f, result));
		TS_ASSERT(!result.upperVisible);
		TS_ASSERT(result.lowerVisible);
	}

	void testLowerCursorUsesInterfaceDestinationGeometry() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.25f, 0.875f,
			0.24f, 0.85f, 0.02f, 0.05f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			10.0f, 20.0f, 800.0f, 200.0f, result));
		TS_ASSERT(result.lowerVisible);
		TS_ASSERT_DELTA(result.lowerX, 202.0f, 0.001f);
		TS_ASSERT_DELTA(result.lowerY, 100.0f, 0.001f);
		TS_ASSERT_DELTA(result.lowerWidth, 16.0f, 0.001f);
		TS_ASSERT_DELTA(result.lowerHeight, 40.0f, 0.001f);
	}

	void testCursorOutsideCropDoesNotEnterDestinationPadding() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.5f, 0.70f,
			0.48f, 0.68f, 0.04f, 0.08f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			100.0f, 25.0f, 600.0f, 150.0f, result));
		TS_ASSERT(!result.lowerVisible);
	}

	void testCursorAtCropEdgeKeepsHotspotAndClipsTextureByDestination() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.0f, 0.75f,
			-0.05f, 0.70f, 0.1f, 0.1f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			20.0f, 30.0f, 800.0f, 200.0f, result));
		TS_ASSERT(result.lowerVisible);
		TS_ASSERT(result.lowerX < 20.0f);
		TS_ASSERT(result.lowerY < 30.0f);
		TS_ASSERT_DELTA(result.lowerWidth, 80.0f, 0.001f);
	}

	void testCropBottomIsExclusive() {
		AndroidMirrorCursorPresentation result;
		TS_ASSERT(deriveAndroidMirrorCursorPresentation(0.5f, 1.0f,
			0.48f, 0.98f, 0.04f, 0.08f, 0.75f, 0.0f, 0.75f, 1.0f, 1.0f,
			0.0f, 0.0f, 800.0f, 200.0f, result));
		TS_ASSERT(!result.upperVisible);
		TS_ASSERT(!result.lowerVisible);
	}
};
