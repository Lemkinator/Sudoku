/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.lemke.commonutils.backup.HmacSha256BackupSigner
import de.lemke.commonutils.backup.core.BackupSigner
import de.lemke.sudoku.BuildConfig
import java.util.Base64

/**
 * Provides the backup signer. The signature stops edits to a backup outside the app, not a determined attacker, because the
 * signing key ships in the release APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupSigningModule {
    @Provides
    fun provideBackupSigner(): BackupSigner =
        HmacSha256BackupSigner(BuildConfig.BACKUP_KEY_ID, Base64.getDecoder().decode(BuildConfig.BACKUP_SIGNING_KEY))
}
