package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/** A ScrollView that grows with its content up to a share of the screen height, then scrolls. */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    /** Fraction of the screen height the content may take before it starts scrolling. */
    var maxScreenFraction = 0.78f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (resources.displayMetrics.heightPixels * maxScreenFraction).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
    }
}
