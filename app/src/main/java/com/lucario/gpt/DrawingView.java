package com.lucario.gpt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

public class DrawingView extends View {
    private Paint paint;
    private Path path;
    private Button saveButton;
    private Button clearButton;

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        path = new Path();

        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(10f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                hideButtons();
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                showButtons();
                break;
            default:
                return false;
        }

        invalidate();
        return true;
    }

    public void setButtons(Button saveButton, Button clearButton) {
        this.saveButton = saveButton;
        this.clearButton = clearButton;
    }

    private void showButtons() {
        if (saveButton != null && clearButton != null) {
            saveButton.setVisibility(View.VISIBLE);
            clearButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideButtons() {
        if (saveButton != null && clearButton != null) {
            saveButton.setVisibility(View.GONE);
            clearButton.setVisibility(View.GONE);
        }
    }
}
