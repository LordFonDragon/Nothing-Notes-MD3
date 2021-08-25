/*
 * Copyright 2021 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.ui.note.adapter

import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.ListNoteItem
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.strikethroughText
import com.maltaisn.notes.sync.BuildConfig
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.ItemHeaderBinding
import com.maltaisn.notes.sync.databinding.ItemMessageBinding
import com.maltaisn.notes.sync.databinding.ItemNoteLabelBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListBinding
import com.maltaisn.notes.sync.databinding.ItemNoteListItemBinding
import com.maltaisn.notes.sync.databinding.ItemNoteTextBinding
import com.maltaisn.notes.ui.note.HighlightHelper
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.utils.RelativeDateFormatter
import java.text.DateFormat

/**
 * If checked items are moved to the bottom and hidden in preview,
 * this is the minimum number of items shown in a list note preview.
 * So if all items are checked, this number of items will be shown, even if they're checked.
 */
private const val MINIMUM_LIST_NOTE_ITEMS = 2

// Constants for start ellipsis of note content to make sure highlight falls in preview.
// These are approximate values, the perfect value varies according to device size, character width, font, etc.
// The current implementation ignores these factors and only accounts for a few settings (list layout, preview lines)
private const val START_ELLIPSIS_THRESHOLD_TITLE = 20
private const val START_ELLIPSIS_DISTANCE_TITLE = 10
private const val START_ELLIPSIS_THRESHOLD_ITEM = 10
private const val START_ELLIPSIS_DISTANCE_ITEM = 4
private const val START_ELLIPSIS_THRESHOLD_TEXT = 15  // per line of preview (-1)
private const val START_ELLIPSIS_THRESHOLD_TEXT_FIRST = 5  // for first line of preview
private const val START_ELLIPSIS_DISTANCE_TEXT = 20

// Start ellipsis threshold is doubled in list layout mode
fun getStartEllipsisThreshold(threshold: Int, adapter: NoteAdapter) =
    threshold * (if (adapter.listLayoutMode == NoteListLayoutMode.GRID) 1 else 2)

sealed class NoteViewHolder(itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    private val dateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateUtils.formatDateTime(itemView.context, date, DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
    }
    private val reminderDateFormatter = RelativeDateFormatter(itemView.resources) { date ->
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }

    protected abstract val cardView: MaterialCardView
    protected abstract val titleTxv: TextView
    protected abstract val dateTxv: TextView
    protected abstract val reminderChip: Chip
    protected abstract val labelGroup: ChipGroup
    protected abstract val actionBtn: MaterialButton

    private val labelViewHolders = mutableListOf<LabelChipViewHolder>()

    open fun bind(adapter: NoteAdapter, item: NoteItem) {
        bindTitle(adapter, item)
        bindDate(adapter, item)
        bindReminder(item)
        bindLabels(adapter, item)
        bindActionBtn(adapter, item)

        // Click listeners
        cardView.isChecked = item.checked
        cardView.setOnClickListener {
            adapter.callback.onNoteItemClicked(item, bindingAdapterPosition)
        }
        cardView.setOnLongClickListener {
            adapter.callback.onNoteItemLongClicked(item, bindingAdapterPosition)
            true
        }
    }

    private fun bindTitle(adapter: NoteAdapter, item: NoteItem) {
        val note = item.note
        var title = note.title
        if (BuildConfig.ENABLE_DEBUG_FEATURES) {
            title += " (${note.id})"
        }
        titleTxv.text = HighlightHelper.getHighlightedText(title, item.titleHighlights,
            adapter.highlightBackgroundColor, adapter.highlightForegroundColor,
            getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_TITLE, adapter),
            START_ELLIPSIS_DISTANCE_TITLE)
        titleTxv.isVisible = title.isNotBlank()
    }

    private fun bindDate(adapter: NoteAdapter, item: NoteItem) {
        val note = item.note
        val dateField = adapter.prefsManager.shownDateField
        val date = when (dateField) {
            ShownDateField.ADDED -> note.addedDate.time
            ShownDateField.MODIFIED -> note.lastModifiedDate.time
            ShownDateField.NONE -> 0L
        }
        dateTxv.text = dateFormatter.format(date, System.currentTimeMillis(), PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS)
        dateTxv.isGone = (dateField == ShownDateField.NONE)
    }

    private fun bindReminder(item: NoteItem) {
        val note = item.note
        reminderChip.isVisible = note.reminder != null
        if (note.reminder != null) {
            reminderChip.text = reminderDateFormatter.format(note.reminder.next.time,
                System.currentTimeMillis(), PrefsManager.MAXIMUM_RELATIVE_DATE_DAYS)
            reminderChip.strikethroughText = note.reminder.done
            reminderChip.isActivated = !note.reminder.done
            reminderChip.setChipIconResource(if (note.reminder.recurrence != null)
                R.drawable.ic_repeat else R.drawable.ic_alarm)
        }
    }

    private fun bindLabels(adapter: NoteAdapter, item: NoteItem) {
        // Show labels in order up to the maximum, then show a +N chip at the end.
        val maxLabels = adapter.prefsManager.maximumPreviewLabels
        if (maxLabels > 0) {
            labelGroup.isVisible = item.labels.isNotEmpty()
            val labels = if (item.labels.size > maxLabels) {
                item.labels.subList(0, maxLabels) +
                        Label(Label.NO_ID, "+${item.labels.size - maxLabels}")
            } else {
                item.labels
            }
            for (label in labels) {
                val viewHolder = adapter.obtainLabelViewHolder()
                labelViewHolders += viewHolder
                viewHolder.bind(label)
                labelGroup.addView(viewHolder.binding.root)
            }
        } else {
            // Don't show labels in preview
            labelGroup.isVisible = false
        }
    }

    private fun bindActionBtn(adapter: NoteAdapter, item: NoteItem) {
        val bottomPadding: Int
        if (item.showMarkAsDone && !item.checked) {
            actionBtn.isVisible = true
            actionBtn.setIconResource(R.drawable.ic_check)
            actionBtn.setText(R.string.action_mark_as_done)
            actionBtn.setOnClickListener {
                adapter.callback.onNoteActionButtonClicked(item, bindingAdapterPosition)
            }
            bottomPadding = R.dimen.note_bottom_padding_with_action
        } else {
            actionBtn.isVisible = false
            bottomPadding = R.dimen.note_bottom_padding_no_action
        }
        cardView.setContentPadding(0, 0, 0,
            cardView.context.resources.getDimensionPixelSize(bottomPadding))
    }

    /**
     * Unbind a previously bound view holder.
     * This is used to free "secondary" view holders.
     */
    open fun unbind(adapter: NoteAdapter) {
        // Free label view holders
        labelGroup.removeViews(0, labelGroup.childCount)
        for (viewHolder in labelViewHolders) {
            adapter.freeLabelViewHolder(viewHolder)
        }
        labelViewHolders.clear()
    }
}

class TextNoteViewHolder(private val binding: ItemNoteTextBinding) :
    NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv
    override val dateTxv = binding.dateTxv
    override val reminderChip = binding.reminderChip
    override val labelGroup = binding.labelGroup
    override val actionBtn = binding.actionBtn

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        require(item.note.type == NoteType.TEXT)

        val contentTxv = binding.contentTxv
        val maxLines = adapter.getMaximumPreviewLines(NoteType.TEXT)
        val ellipsisThreshold = getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_TEXT, adapter) *
                (maxLines - 1) + START_ELLIPSIS_THRESHOLD_TEXT_FIRST
        contentTxv.isVisible = item.note.content.isNotBlank()
        contentTxv.text = HighlightHelper.getHighlightedText(item.note.content.trim(), item.contentHighlights,
            adapter.highlightBackgroundColor, adapter.highlightForegroundColor,
            ellipsisThreshold, START_ELLIPSIS_DISTANCE_TEXT)
        contentTxv.maxLines = maxLines
    }
}

class ListNoteViewHolder(private val binding: ItemNoteListBinding) : NoteViewHolder(binding.root) {

    override val cardView = binding.cardView
    override val titleTxv = binding.titleTxv
    override val dateTxv = binding.dateTxv
    override val reminderChip = binding.reminderChip
    override val labelGroup = binding.labelGroup
    override val actionBtn = binding.actionBtn

    private val itemViewHolders = mutableListOf<ListNoteItemViewHolder>()

    override fun bind(adapter: NoteAdapter, item: NoteItem) {
        super.bind(adapter, item)
        // TODO some logi

        require(item.note.type == NoteType.LIST)
        require(itemViewHolders.isEmpty())

        val noteItems = item.note.listItems

        val itemsLayout = binding.itemsLayout
        itemsLayout.isVisible = noteItems.isNotEmpty()

        // Add the first few items in list note using view holders in pool.
        // If not moving checked items to the bottom, show items in order.
        // Otherwise, show only unchecked items and move the rest to overflow (+N checked items).
        val maxItems = adapter.getMaximumPreviewLines(NoteType.LIST)
        val minItems = minOf(MINIMUM_LIST_NOTE_ITEMS, maxItems)
        val showChecked = !adapter.prefsManager.moveCheckedToBottom
        val itemHighlights = HighlightHelper.splitListNoteHighlightsByItem(noteItems, item.contentHighlights)
        var onlyCheckedInOverflow = true
        for ((i, noteItem) in noteItems.withIndex()) {
            if (itemViewHolders.size < maxItems && (showChecked || !noteItem.checked)) {
                val viewHolder = adapter.obtainListNoteItemViewHolder()
                viewHolder.bind(adapter, noteItem, itemHighlights[i])
                itemsLayout.addView(viewHolder.binding.root, itemViewHolders.size)
                itemViewHolders += viewHolder
            } else if (!noteItem.checked) {
                onlyCheckedInOverflow = false
            }
        }

        if (!showChecked && itemViewHolders.size < minItems) {
            // Checked items were omitted, but that leaves too few items, add some checked items
            for ((i, noteItem) in noteItems.withIndex()) {
                if (noteItem.checked) {
                    val viewHolder = adapter.obtainListNoteItemViewHolder()
                    viewHolder.bind(adapter, noteItem, itemHighlights[i])
                    itemsLayout.addView(viewHolder.binding.root, itemViewHolders.size)
                    itemViewHolders += viewHolder
                    if (itemViewHolders.size == minItems) {
                        break
                    }
                }
            }
        }

        // Show a label indicating the number of items not shown.
        val infoTxv = binding.infoTxv
        val overflowCount = noteItems.size - itemViewHolders.size
        infoTxv.isVisible = overflowCount > 0
        if (overflowCount > 0) {
            infoTxv.text = adapter.context.resources.getQuantityString(
                if (onlyCheckedInOverflow) {
                    R.plurals.note_list_item_info_checked
                } else {
                    R.plurals.note_list_item_info
                }, overflowCount, overflowCount)
        }
    }

    override fun unbind(adapter: NoteAdapter) {
        super.unbind(adapter)
        // Free view holders used by the item.
        binding.itemsLayout.removeViews(0, binding.itemsLayout.childCount - 1)
        for (viewHolder in itemViewHolders) {
            adapter.freeListNoteItemViewHolder(viewHolder)
        }
        itemViewHolders.clear()
    }
}

class MessageViewHolder(private val binding: ItemMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: MessageItem, adapter: NoteAdapter) {
        binding.messageTxv.text = adapter.context.getString(item.message, *item.args.toTypedArray())
        binding.closeImv.setOnClickListener {
            adapter.callback.onMessageItemDismissed(item, bindingAdapterPosition)
            adapter.notifyItemRemoved(bindingAdapterPosition)
        }

        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

class HeaderViewHolder(private val binding: ItemHeaderBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HeaderItem) {
        binding.titleTxv.setText(item.title)
        (itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
    }
}

/**
 * A view holder for displayed an item in a list note view holder.
 * This is a "secondary" view holder, it is held by another view holder.
 */
class ListNoteItemViewHolder(val binding: ItemNoteListItemBinding) {

    fun bind(adapter: NoteAdapter, item: ListNoteItem, highlights: List<IntRange>) {
        binding.contentTxv.apply {
            text = HighlightHelper.getHighlightedText(item.content, highlights,
                adapter.highlightBackgroundColor, adapter.highlightForegroundColor,
                getStartEllipsisThreshold(START_ELLIPSIS_THRESHOLD_ITEM, adapter),
                START_ELLIPSIS_DISTANCE_ITEM)
            strikethroughText = item.checked && adapter.callback.strikethroughCheckedItems
        }

        binding.checkboxImv.setImageResource(if (item.checked) {
            R.drawable.ic_checkbox_on
        } else {
            R.drawable.ic_checkbox_off
        })
    }
}

/**
 * A view holder for a label chip displayed in note view holders.
 * This is a "secondary" view holder, it is held by another view holder.
 */
class LabelChipViewHolder(val binding: ItemNoteLabelBinding) {

    fun bind(label: Label) {
        binding.labelChip.text = label.name
    }
}
