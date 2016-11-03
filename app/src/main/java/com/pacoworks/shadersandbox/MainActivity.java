package com.pacoworks.shadersandbox;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ImageView viewById = (ImageView) findViewById(R.id.sample_image);
        final BitmapDrawable drawable = (BitmapDrawable) viewById.getDrawable();
        final ShaderTool shaderTool = new ShaderTool();
        final Bitmap result = shaderTool.processBitmap(new GPUImageFilter(), drawable.getBitmap());
        viewById.setImageBitmap(result);
    }
}
