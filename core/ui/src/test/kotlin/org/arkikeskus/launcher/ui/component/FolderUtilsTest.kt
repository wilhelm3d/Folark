package org.arkikeskus.launcher.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderUtilsTest {

    @Test
    fun isDefaultOrBlankFolderName_returnsTrueForEmptyAndBlank() {
        assertThat(isDefaultOrBlankFolderName("")).isTrue()
        assertThat(isDefaultOrBlankFolderName("   ")).isTrue()
    }

    @Test
    fun isDefaultOrBlankFolderName_returnsTrueForDefaultNames() {
        assertThat(isDefaultOrBlankFolderName("Folder")).isTrue()
        assertThat(isDefaultOrBlankFolderName("folder")).isTrue()
        assertThat(isDefaultOrBlankFolderName("Kansio")).isTrue()
        assertThat(isDefaultOrBlankFolderName("kansio")).isTrue()
        assertThat(isDefaultOrBlankFolderName("CustomDefault", "CustomDefault")).isTrue()
    }

    @Test
    fun isDefaultOrBlankFolderName_returnsFalseForCustomName() {
        assertThat(isDefaultOrBlankFolderName("Social")).isFalse()
        assertThat(isDefaultOrBlankFolderName("Games")).isFalse()
        assertThat(isDefaultOrBlankFolderName("Work & Study")).isFalse()
    }
}
