#!/usr/bin/env bash
# Stop paying for the cluster when you are not demoing.
#   ./cost-control.sh stop     -> node VM shuts down (no compute charge)
#   ./cost-control.sh start    -> back up in ~5 min (public IP may change)
#   ./cost-control.sh destroy  -> delete EVERYTHING after the viva is graded
set -euo pipefail
RG=cicd-demo-rg
AKS=cicd-demo-aks

case "${1:-}" in
  stop)    az aks stop  -g "$RG" -n "$AKS" ;;
  start)   az aks start -g "$RG" -n "$AKS" && az aks get-credentials -g "$RG" -n "$AKS" --overwrite-existing ;;
  status)  az aks show  -g "$RG" -n "$AKS" --query powerState -o tsv ;;
  destroy) az group delete -n "$RG" --yes --no-wait && echo "Deleting resource group $RG" ;;
  *) echo "usage: $0 {stop|start|status|destroy}"; exit 1 ;;
esac
