/*
 * Copyright 2020 Nicolas Maltais
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

package com.maltaisn.notes.ui.edit.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.notes.R


class EditAdapter(val context: Context) :
        ListAdapter<EditListItem, RecyclerView.ViewHolder>(EditDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TITLE -> EditTitleViewHolder(inflater.inflate(
                    R.layout.item_edit_title, parent, false))
            VIEW_TYPE_CONTENT -> EditContentViewHolder(inflater.inflate(
                    R.layout.item_edit_content, parent, false))
            VIEW_TYPE_ITEM -> EditItemViewHolder(inflater.inflate(
                    R.layout.item_edit_item, parent, false))
            VIEW_TYPE_ITEM_ADD -> EditItemAddViewHolder(inflater.inflate(
                    R.layout.item_edit_item_add, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is EditTitleViewHolder -> holder.bind(item as EditTitleItem)
            is EditContentViewHolder -> holder.bind(item as EditContentItem)
            is EditItemViewHolder -> holder.bind(item as EditItemItem)
            is EditItemAddViewHolder -> holder.bind(item as EditItemAddItem)
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).type

    companion object {
        const val VIEW_TYPE_TITLE = 0
        const val VIEW_TYPE_CONTENT = 1
        const val VIEW_TYPE_ITEM = 2
        const val VIEW_TYPE_ITEM_ADD = 3
    }
}
