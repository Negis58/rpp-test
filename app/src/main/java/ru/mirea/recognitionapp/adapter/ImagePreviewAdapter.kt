package ru.mirea.recognitionapp.adapter

import android.graphics.Bitmap
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_image_preview.view.*
import ru.mirea.recognitionapp.R
import java.util.*

class ImagePreviewAdapter(
    private val bitmaps: MutableList<Bitmap?>
) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    var check = 0

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ImageViewHolder =
            ImageViewHolder(LayoutInflater
                .from(viewGroup.context)
                .inflate(R.layout.item_image_preview, viewGroup, false))


    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) = holder.bind(position)

    override fun getItemCount(): Int = bitmaps.size

    override fun getItemId(position: Int): Long = position.toLong()

    fun add(bitmap: Bitmap?) {
        insert(bitmap, bitmaps.size)
    }

    fun insert(bitmap: Bitmap?, position: Int) {
        bitmaps.add(position, bitmap)
        notifyItemInserted(position)
    }

    fun remove(position: Int) {
        bitmaps.removeAt(position)
        notifyDataSetChanged()
    }

    fun clearAll() {
        for (i in bitmaps.indices) {
            if (bitmaps[i] != null) bitmaps[i]!!.recycle()
        }
        bitmaps.clear()
        check = 0
        notifyDataSetChanged()
    }

    fun addAll(bitmaps: ArrayList<Bitmap?>) {
        val startIndex = bitmaps.size
        this.bitmaps.addAll(startIndex, bitmaps)
        notifyItemRangeInserted(startIndex, bitmaps.size)
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int) {
            if (position == check) itemView.layoutCheck.visibility = View.VISIBLE
            else itemView.layoutCheck.visibility = View.GONE

            val metrics = itemView.context.resources.displayMetrics
            val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60f, metrics).toInt()
            val bmp = Bitmap.createScaledBitmap(bitmaps[position]!!, size, size, false)
            itemView.imagePreview.setImageBitmap(bmp)
        }
    }
}