/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.lmax.disruptor.util.Util;

/**
 * 各种sequencer的基类（单/多） 提供公有的功能，如gating sequences的管理（add/remove） 和 当前光标的所在位置。
 * 作用就是管理消费者追踪序列和表示生产者当前序列。
 */
public abstract class AbstractSequencer implements Sequencer
{
	// 用来对gatingSequences做原子操作的; Sequence[]里面存储的是消费者处理到的序列。
    private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");
    
    // 表示环形数组的大小
    protected final int bufferSize;
    // 标识消费者追上生产者时所使用的等待策略
    protected final WaitStrategy waitStrategy;
    // 生产者的已经发布到的sequence; cursor这个序列就是用来标识生产者的当前序列
    protected final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    // 消费者处理到的序列对象
    protected volatile Sequence[] gatingSequences = new Sequence[0];

    /**
     * Create with the specified buffer size and wait strategy.
     *
     * @param bufferSize   The total number of entries, must be a positive power of 2.
     * @param waitStrategy The wait strategy used by this sequencer
     */
	/**
	 *
	 * 利用等待策略和环的大小。检查队列大小是否是2^n; 判断buffersize大小;
	 * 
	 * @param bufferSize   entries总数必须是2的正幂。
	 * @param waitStrategy sequencer使用的等待策略
	 */
    public AbstractSequencer(int bufferSize, WaitStrategy waitStrategy)
    {
        if (bufferSize < 1)
        {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    /**
     * 获取生产者的序列
     * 
     * @see Sequencer#getCursor()
     */
    @Override
    public final long getCursor()
    {
        return cursor.get();
    }

    /**
     * 获取大小
     * 
     * @see Sequencer#getBufferSize()
     */
    @Override
    public final int getBufferSize()
    {
        return bufferSize;
    }

    /**
     * 把事件消费者序列维护到gatingSequences
     * 
     * @see Sequencer#addGatingSequences(Sequence...)
     */
    @Override
    public final void addGatingSequences(Sequence... gatingSequences)
    {
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, this, gatingSequences);
    }

    /**
     * 从gatingSequence移除序列
     * 
     * @see Sequencer#removeGatingSequence(Sequence)
     */
    @Override
    public boolean removeGatingSequence(Sequence sequence)
    {
        return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
    }

    /**
     * 获取gatingSequence中消费者处理到最小的序列值
     * 
     * @see Sequencer#getMinimumSequence()
     */
    @Override
    public long getMinimumSequence()
    {
        return Util.getMinimumSequence(gatingSequences, cursor.get());
    }

    /**
     * 创建了一个序列栅栏
     * 
     * @see Sequencer#newBarrier(Sequence...)
     */
    @Override
    public SequenceBarrier newBarrier(Sequence... sequencesToTrack)
    {
        return new ProcessingSequenceBarrier(this, waitStrategy, cursor, sequencesToTrack);
    }

	/**
	 * 为此序列创建一个事件轮询器，它将使用提供的数据提供程序和门控序列。
	 * 
	 * @param dataProvider    此事件轮询器用户的数据源
	 * @param gatingSequences 序列
	 * @return RingBuffer和提供的序列进行轮询
	 */
    @Override
    public <T> EventPoller<T> newPoller(DataProvider<T> dataProvider, Sequence... gatingSequences)
    {
        return EventPoller.newInstance(dataProvider, this, new Sequence(), cursor, gatingSequences);
    }

    @Override
    public String toString()
    {
        return "AbstractSequencer{" +
            "waitStrategy=" + waitStrategy +
            ", cursor=" + cursor +
            ", gatingSequences=" + Arrays.toString(gatingSequences) +
            '}';
    }
}