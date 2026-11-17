package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.entity.PrepStation;
import com.notapos.repository.PrepStationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for PrepStation operations.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class PrepStationService {
    
    private final PrepStationRepository prepStationRepository;

    @Autowired
    public PrepStationService(PrepStationRepository prepStationRepository) {
        this.prepStationRepository = prepStationRepository;
    }

    public List<PrepStation> getAllPrepStations() {         // Get all prep stations
        return prepStationRepository.findAll();
    }

    public List<PrepStation> getActivePrepStations() {      // get all active prep stations
        return prepStationRepository.findByIsActive(true);
    }

    public Optional<PrepStation> getPrepStationById(Long id) {      // get prep station by ID
        return prepStationRepository.findById(id);
    }

    public Optional<PrepStation> getPrepStationByName(String name) {    // get prep station by name
        return prepStationRepository.findByName(name);
    }

    @Transactional
    public PrepStation createPrepStation(PrepStation prepStation) {         // create new prep station
        return prepStationRepository.save(prepStation);
    }

    @Transactional
    public PrepStation updatePrepStation(Long id, PrepStation updatedStation) {     // Update a prep station
        PrepStation existing = prepStationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prep station not found with id: " + id));

        existing.setName(updatedStation.getName());
        existing.setDescription(updatedStation.getDescription());
        existing.setIsActive(updatedStation.getIsActive());

        return prepStationRepository.save(existing);
    }

    @Transactional
    public void deletePrepStation(Long id) {                        // delete a prep station
        prepStationRepository.deleteById(id);
    }
}
