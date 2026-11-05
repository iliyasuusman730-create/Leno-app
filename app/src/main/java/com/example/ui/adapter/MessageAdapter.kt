package com.example.ui.adapter

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.entity.MessageEntity

/**
 * MessageAdapter for displaying glassmorphic chat bubbles with
 * interactive long-press actionsRow (Reply, Forward, Delete).
 */
class MessageAdapter(
    private var currentUserId: String = "",
    private var onActionReply: ((MessageEntity) -> Unit)? = null,
    private var onActionForward: ((MessageEntity) -> Unit)? = null,
    private var onActionDelete: ((MessageEntity) -> Unit)? = null
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<MessageEntity>()
    private var expandedMessageId: String? = null

    fun setUserId(userId: String) {
        currentUserId = userId
        notifyDataSetChanged()
    }

    fun submitList(newMessages: List<MessageEntity>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: MessageEntity) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * Requirement 5: deleteMessage function
     */
    fun deleteMessage(message: MessageEntity) {
        val index = messages.indexOfFirst { it.messageId == message.messageId }
        if (index != -1) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
        if (expandedMessageId == message.messageId) {
            expandedMessageId = null
        }
        onActionDelete?.invoke(message)
    }

    fun deleteMessage(position: Int) {
        if (position in messages.indices) {
            val msg = messages[position]
            deleteMessage(msg)
        }
    }

    /**
     * Requirement 5: replyMessage function
     */
    fun replyMessage(message: MessageEntity) {
        expandedMessageId = null
        notifyDataSetChanged()
        onActionReply?.invoke(message)
    }

    /**
     * Requirement 5: forwardMessage function
     */
    fun forwardMessage(message: MessageEntity) {
        expandedMessageId = null
        notifyDataSetChanged()
        onActionForward?.invoke(message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isFromMe = message.senderId == currentUserId

        holder.messageText.text = message.text

        // Glassmorphism styling based on sender
        val bubbleParams = holder.bubbleContainer.layoutParams as LinearLayout.LayoutParams
        val actionsParams = holder.actionsRow.layoutParams as LinearLayout.LayoutParams

        if (isFromMe) {
            holder.messageText.setBackgroundResource(R.drawable.bubble_glass_me)
            holder.messageText.setTextColor(Color.WHITE)
            bubbleParams.gravity = Gravity.END
            actionsParams.gravity = Gravity.END
        } else {
            holder.messageText.setBackgroundResource(R.drawable.bubble_glass_other)
            holder.messageText.setTextColor(Color.parseColor("#183A2D"))
            bubbleParams.gravity = Gravity.START
            actionsParams.gravity = Gravity.START
        }
        holder.bubbleContainer.layoutParams = bubbleParams
        holder.actionsRow.layoutParams = actionsParams

        // Requirement 4: On long press message, make it visible, on second long press gone
        val isExpanded = expandedMessageId == message.messageId
        holder.actionsRow.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.messageText.setOnLongClickListener {
            if (expandedMessageId == message.messageId) {
                expandedMessageId = null
                holder.actionsRow.visibility = View.GONE
            } else {
                expandedMessageId = message.messageId
                holder.actionsRow.visibility = View.VISIBLE
            }
            true
        }

        // Action buttons
        holder.actionReply.setOnClickListener {
            replyMessage(message)
        }

        holder.actionForward.setOnClickListener {
            forwardMessage(message)
        }

        holder.actionDelete.setOnClickListener {
            deleteMessage(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageRootContainer: LinearLayout = itemView.findViewById(R.id.messageRootContainer)
        val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val actionsRow: LinearLayout = itemView.findViewById(R.id.actionsRow)
        val actionReply: ImageView = itemView.findViewById(R.id.actionReply)
        val actionForward: ImageView = itemView.findViewById(R.id.actionForward)
        val actionDelete: ImageView = itemView.findViewById(R.id.actionDelete)
    }
}
