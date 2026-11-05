#!/usr/bin/env bash
# One-time cloud setup. Run in Azure Cloud Shell (portal.azure.com -> >_ icon), which already has az + kubectl.
# Prints the 4 values you paste into GitHub -> Settings -> Secrets and variables -> Actions.
set -euo pipefail

RG=cicd-demo-rg
LOCATION=eastasia   # Azure for Students only allows certain regions: check with az policy assignment list
AKS=cicd-demo-aks
# ACR names must be globally unique, lowercase letters/digits only
ACR=${ACR:-cicddemo$RANDOM}

echo ">> Registering providers (first time only, takes a minute)"
az provider register --namespace Microsoft.ContainerService --wait
az provider register --namespace Microsoft.ContainerRegistry --wait

echo ">> Resource group"
az group create -n "$RG" -l "$LOCATION" -o table

echo ">> Container registry (Basic = cheapest)"
az acr create -g "$RG" -n "$ACR" --sku Basic --admin-enabled true -o table

echo ">> AKS cluster: 1 small node, free control plane"
az aks create -g "$RG" -n "$AKS" \
  --tier free \
  --node-count 1 \
  --node-vm-size Standard_B2s_v2 \
  --generate-ssh-keys \
  -o table

echo ">> Fetching credentials"
az aks get-credentials -g "$RG" -n "$AKS" --overwrite-existing
kubectl get nodes

ACR_SERVER=$(az acr show -n "$ACR" --query loginServer -o tsv)
ACR_USERNAME=$(az acr credential show -n "$ACR" --query username -o tsv)
ACR_PASSWORD=$(az acr credential show -n "$ACR" --query 'passwords[0].value' -o tsv)
KUBE_CONFIG=$(base64 -w0 ~/.kube/config)

cat <<EOF

================ ADD THESE AS GITHUB SECRETS ================
ACR_SERVER    = $ACR_SERVER
ACR_USERNAME  = $ACR_USERNAME
ACR_PASSWORD  = $ACR_PASSWORD
KUBE_CONFIG   = (long value below, copy all of it)
$KUBE_CONFIG
=============================================================
EOF
