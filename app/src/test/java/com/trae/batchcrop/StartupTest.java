package com.trae.batchcrop;

import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * 启动冒烟测试：在 JVM 上真实执行 Application / Activity 的创建与布局解析，
 * 用于在无真机环境下复现「打开即闪退」。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StartupTest {

    @Test
    public void mainActivityInflates() {
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class).setup();
        assertNotNull(controller.get().findViewById(R.id.root));
        assertNotNull(controller.get().findViewById(R.id.imageRecycler));
        assertNotNull(controller.get().findViewById(R.id.nextBtn));
    }

    @Test
    public void cropActivityInflates() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), CropActivity.class);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse("content://media/external/images/media/1"));
        intent.putParcelableArrayListExtra(CropActivity.EXTRA_URIS, uris);
        intent.putExtra(CropActivity.EXTRA_COUNT, 3);

        ActivityController<CropActivity> controller =
                Robolectric.buildActivity(CropActivity.class, intent).setup();
        assertNotNull(controller.get().findViewById(R.id.editor));
        assertNotNull(controller.get().findViewById(R.id.chipGroup));
    }

    @Test
    public void resultActivityInflates() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), ResultActivity.class);
        intent.putExtra(ResultActivity.EXTRA_GENERATED, 0);
        intent.putExtra(ResultActivity.EXTRA_FAILED, 0);
        intent.putExtra(ResultActivity.EXTRA_IMAGES, 0);

        ActivityController<ResultActivity> controller =
                Robolectric.buildActivity(ResultActivity.class, intent).setup();
        assertNotNull(controller.get().findViewById(R.id.summary));
        assertNotNull(controller.get().findViewById(R.id.doneBtn));
    }
}
