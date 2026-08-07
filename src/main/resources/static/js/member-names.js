window.MemberNames = {
    async resolve(schoolId, userIds) {
        const batchSize = 100;
        const uniqueIds = [...new Map(
            (userIds || [])
                .filter(id => id !== null && id !== undefined && id !== '')
                .map(id => [String(id), id])
        ).values()];
        if (!schoolId || uniqueIds.length === 0) {
            return [];
        }

        const resolvedNames = [];
        for (let offset = 0; offset < uniqueIds.length; offset += batchSize) {
            const params = new URLSearchParams();
            uniqueIds.slice(offset, offset + batchSize)
                .forEach(id => params.append('userIds', id));
            const response = await fetch(
                `/api/admin/schools/${encodeURIComponent(schoolId)}/memberships/names?${params.toString()}`,
                { headers: { 'X-School-ID': schoolId } }
            );
            if (!response.ok) {
                throw new Error(`Member-name lookup failed with status ${response.status}.`);
            }
            const names = await response.json();
            resolvedNames.push(...names);
        }
        return resolvedNames.map(name => ({ ...name, email: name.email || '' }));
    }
};
