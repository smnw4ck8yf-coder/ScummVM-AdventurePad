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

#include "backends/graphics/android/upper-presentation.h"

class AndroidUpperPresentationTestSuite : public CxxTest::TestSuite {
public:
	void testBottomSplitProducesExactUpperComplement() {
		AndroidUpperPresentationCrop crop;
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0.75f, 1, 1, crop), kUpperPresentationExpanded);
		TS_ASSERT_DELTA(crop.left, 0.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.top, 0.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.right, 1.0f, 0.0001f);
		TS_ASSERT_DELTA(crop.bottom, 0.75f, 0.0001f);
	}

	void testUnsupportedAndDegenerateComplements() {
		AndroidUpperPresentationCrop crop;
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0.2f, 0.2f, 0.8f, 0.8f, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0.1f, 0.75f, 1, 1, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0, 1, 0.25f, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0, 0.25f, 1, crop),
			kUpperPresentationUnsupportedShape);
		TS_ASSERT_EQUALS(deriveAndroidUpperGameplayCrop(0, 0.01f, 1, 1, crop),
			kUpperPresentationInvalidCrop);
	}
};
