package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectTeam;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.ProjectTeamRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * One place that knows which teams a project has.
 * <p>
 * A project's teams live in pm_project_teams. Project.teamId stays as the "primary" (first) team so
 * older code keeps working, and it is always included here, so projects created before this table
 * existed still report their team even if the data was never copied over.
 */
@Service
@RequiredArgsConstructor
public class ProjectTeamService {

    private final ProjectTeamRepository repo;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;

    /** All team ids of a project, primary team first. Empty when the project has no team. */
    @Transactional(readOnly = true)
    public Set<Long> teamIdsOf(Long projectId) {
        Set<Long> out = new LinkedHashSet<>();
        if (projectId == null) return out;
        projectRepository.findById(projectId).map(Project::getTeamId).ifPresent(out::add);
        repo.findByProjectIdOrderByIdAsc(projectId).forEach(pt -> out.add(pt.getTeamId()));
        return out;
    }

    /** Same as {@link #teamIdsOf(Long)} for many projects at once (list pages). */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> teamIdsByProject(Collection<Project> projects) {
        Map<Long, List<Long>> out = new HashMap<>();
        if (projects == null || projects.isEmpty()) return out;
        for (Project p : projects) {
            List<Long> l = new ArrayList<>();
            if (p.getTeamId() != null) l.add(p.getTeamId());
            out.put(p.getId(), l);
        }
        for (ProjectTeam pt : repo.findByProjectIdIn(out.keySet())) {
            List<Long> l = out.get(pt.getProjectId());
            if (l != null && !l.contains(pt.getTeamId())) l.add(pt.getTeamId());
        }
        return out;
    }

    /** Ids of projects that have any of these teams (used for "my projects"). */
    @Transactional(readOnly = true)
    public Set<Long> projectIdsOfTeams(Collection<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) return new HashSet<>();
        return new HashSet<>(repo.findProjectIdsByTeamIdIn(teamIds));
    }

    /**
     * Makes {@code teamIds} the project's teams (duplicates removed, order kept). The first one becomes the
     * primary team. Pass an empty list to remove all teams. Unknown team ids are rejected.
     */
    @Transactional
    public List<Long> replace(Project p, Collection<Long> teamIds) {
        LinkedHashSet<Long> clean = new LinkedHashSet<>();
        if (teamIds != null) {
            for (Long id : teamIds) {
                if (id == null || id <= 0) continue;
                if (!teamRepository.existsById(id)) throw new ResourceNotFoundException("Team not found: " + id);
                clean.add(id);
            }
        }
        repo.deleteByProjectId(p.getId());
        for (Long id : clean) {
            repo.save(ProjectTeam.builder().projectId(p.getId()).teamId(id).build());
        }
        p.setTeamId(clean.isEmpty() ? null : clean.iterator().next());
        projectRepository.save(p);
        return new ArrayList<>(clean);
    }

    /** Called when a team is deleted. */
    @Transactional
    public void removeTeam(Long teamId) {
        repo.deleteByTeamId(teamId);
    }
}