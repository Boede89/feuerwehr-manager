package de.feuerwehr.manager.api;

import de.feuerwehr.manager.unit.Unit;
import de.feuerwehr.manager.unit.UnitService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
public class UnitRestController {

    private final UnitService unitService;

    @GetMapping
    public List<UnitDto> listActive() {
        return unitService.findActiveOrdered().stream().map(UnitDto::from).toList();
    }

    public record UnitDto(long id, String name, boolean active) {
        static UnitDto from(Unit u) {
            return new UnitDto(u.getId(), u.getName(), u.isActive());
        }
    }
}
