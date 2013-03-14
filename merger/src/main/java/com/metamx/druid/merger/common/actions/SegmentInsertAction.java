package com.metamx.druid.merger.common.actions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metamx.common.ISE;
import com.metamx.druid.client.DataSegment;
import com.metamx.druid.merger.common.TaskLock;
import com.metamx.druid.merger.common.task.Task;
import com.metamx.emitter.service.ServiceMetricEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Set;

public class SegmentInsertAction implements TaskAction<Void>
{
  @JsonIgnore
  private final Set<DataSegment> segments;

  @JsonIgnore
  private final boolean allowOlderVersions;

  public SegmentInsertAction(Set<DataSegment> segments)
  {
    this(segments, false);
  }

  @JsonCreator
  public SegmentInsertAction(
      @JsonProperty("segments") Set<DataSegment> segments,
      @JsonProperty("allowOlderVersions") boolean allowOlderVersions
  )
  {
    this.segments = ImmutableSet.copyOf(segments);
    this.allowOlderVersions = allowOlderVersions;
  }

  @JsonProperty
  public Set<DataSegment> getSegments()
  {
    return segments;
  }

  @JsonProperty
  public boolean isAllowOlderVersions()
  {
    return allowOlderVersions;
  }

  public SegmentInsertAction withAllowOlderVersions(boolean _allowOlderVersions)
  {
    return new SegmentInsertAction(segments, _allowOlderVersions);
  }

  public TypeReference<Void> getReturnTypeReference()
  {
    return new TypeReference<Void>() {};
  }

  @Override
  public Void perform(Task task, TaskActionToolbox toolbox)
  {
    if(!toolbox.taskLockCoversSegments(task, segments, allowOlderVersions)) {
      throw new ISE("Segments not covered by locks for task: %s", task.getId());
    }

    try {
      toolbox.getMergerDBCoordinator().announceHistoricalSegments(segments);

      // Emit metrics
      final ServiceMetricEvent.Builder metricBuilder = new ServiceMetricEvent.Builder()
          .setUser2(task.getDataSource())
          .setUser4(task.getType());

      for (DataSegment segment : segments) {
        metricBuilder.setUser5(segment.getInterval().toString());
        toolbox.getEmitter().emit(metricBuilder.build("indexer/segment/bytes", segment.getSize()));
      }

      return null;
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}