/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

/*
 * Cross-platform JNI bridge template.
 *
 * Keep the implementation within the JNI API when possible. The same source
 * can then be built on Windows, macOS, Linux, and FreeBSD by JNIBuilder.
 */
#include <jni.h>

JNIEXPORT jstring JNICALL Java_com_example_NativeBridge_echo
  (JNIEnv *env, jobject self, jstring value) {
    (void) self;
    if (value == NULL) return NULL;

    /* Replace this body with the native operation and check JNI exceptions. */
    return (*env)->NewLocalRef(env, value);
}
