// Keep the selected priority fixed. The other weights share the remaining budget.
export function rebalanceWeights(weights, changedKey, value, lockedKey = "") {
  if (changedKey === lockedKey) return { ...weights };

  const result = { ...weights };
  const lockedWeight = lockedKey ? weights[lockedKey] : 0;
  const budget = 100 - lockedWeight;
  const maximum = lockedKey ? lockedWeight - 1 : 100;
  const target = Math.round(Number(value) || 0);
  const otherKeys = Object.keys(weights).filter(
    (key) => key !== changedKey && key !== lockedKey,
  );
  if (otherKeys.length === 0 || maximum * (otherKeys.length + 1) < budget) {
    throw new Error(
      "These saved weights conflict with the selected priority. Choose a strategy again to reset its weights.",
    );
  }
  // Leave enough room for the other criteria when a priority limits their maximum.
  const minimum = Math.max(0, budget - otherKeys.length * maximum);
  result[changedKey] = Math.max(minimum, Math.min(target, maximum, budget));

  for (const key of otherKeys) result[key] = 0;
  let remaining = budget - result[changedKey];
  // Distribute one percentage point at a time. At most 100 iterations are needed.
  while (remaining > 0) {
    for (const key of otherKeys) {
      if (remaining === 0) break;
      if (result[key] < maximum) {
        result[key] += 1;
        remaining -= 1;
      }
    }
  }
  return result;
}
