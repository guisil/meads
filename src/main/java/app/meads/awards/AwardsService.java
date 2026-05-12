package app.meads.awards;

import java.util.UUID;

public interface AwardsService {

    Publication publish(UUID divisionId, UUID adminUserId);
}
