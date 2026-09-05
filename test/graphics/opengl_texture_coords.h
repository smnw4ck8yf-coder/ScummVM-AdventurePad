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

#include "graphics/opengl/texture.h"

class OpenGLTextureCoordsTestSuite : public CxxTest::TestSuite {
public:
	void testCropRespectsAllCanonicalOrientations() {
		const GLfloat orientations[][8] = {
			{ 0, 0, 1, 0, 0, 1, 1, 1 },
			{ 0, 1, 1, 1, 0, 0, 1, 0 },
			{ 0, 1, 0, 0, 1, 1, 1, 0 },
			{ 1, 1, 0, 1, 1, 0, 0, 0 },
			{ 1, 0, 1, 1, 0, 0, 0, 1 }
		};
		for (uint orientation = 0; orientation < ARRAYSIZE(orientations); ++orientation) {
			GLfloat clipped[8];
			OpenGL::Texture::calculateClippedTexCoords(
				orientations[orientation], 0.25f, 0.5f, 0.75f, 1.0f, clipped);
			for (int component = 0; component < 8; ++component) {
				TS_ASSERT(clipped[component] >= 0.0f);
				TS_ASSERT(clipped[component] <= 1.0f);
			}
			const GLfloat expected =
				(orientations[orientation][0] * 0.75f + orientations[orientation][2] * 0.25f) * 0.5f +
				(orientations[orientation][4] * 0.75f + orientations[orientation][6] * 0.25f) * 0.5f;
			TS_ASSERT_DELTA(clipped[0], expected,
				0.0001f);
		}
	}
};
